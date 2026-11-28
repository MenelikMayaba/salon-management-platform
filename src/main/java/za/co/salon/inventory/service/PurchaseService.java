package za.co.salon.inventory.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.business.repository.BusinessRepository;
import za.co.salon.catalogue.entity.Product;
import za.co.salon.catalogue.repository.ProductRepository;
import za.co.salon.finance.posting.JournalPostingService;
import za.co.salon.finance.posting.LineSpec;
import za.co.salon.inventory.dto.PurchaseItemResponse;
import za.co.salon.inventory.dto.PurchaseRequest;
import za.co.salon.inventory.dto.PurchaseItemRequest;
import za.co.salon.inventory.dto.PayPurchaseRequest;
import za.co.salon.inventory.dto.PurchaseResponse;
import za.co.salon.inventory.entity.Purchase;
import za.co.salon.inventory.entity.PurchaseItem;
import za.co.salon.inventory.entity.Supplier;
import za.co.salon.inventory.repository.PurchaseItemRepository;
import za.co.salon.inventory.repository.PurchaseRepository;
import za.co.salon.inventory.repository.SupplierRepository;
import za.co.salon.shared.security.BusinessAuthorizationService;
import za.co.salon.shared.security.BusinessContext;
import za.co.salon.shared.security.SecurityUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Purchases: draft creation, receipt (stock in + AP/Inventory posting) and
 * payment (AP cleared against cash/bank) as three separate events per
 * blueprint 3.7/8.2. Receiving is atomic with the WAC recalculation - the
 * balance rows lock inside this same transaction.
 */
@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final JournalPostingService postingService;
    private final BusinessRepository businessRepository;
    private final BusinessAuthorizationService authz;
    private final BusinessContext businessContext;

    public PurchaseService(PurchaseRepository purchaseRepository,
                           PurchaseItemRepository purchaseItemRepository,
                           SupplierRepository supplierRepository,
                           ProductRepository productRepository,
                           InventoryService inventoryService,
                           JournalPostingService postingService,
                           BusinessRepository businessRepository,
                           BusinessAuthorizationService authz,
                           BusinessContext businessContext) {
        this.purchaseRepository = purchaseRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.postingService = postingService;
        this.businessRepository = businessRepository;
        this.authz = authz;
        this.businessContext = businessContext;
    }

    @Transactional
    public PurchaseResponse createDraft(PurchaseRequest request) {
        Long businessId = businessContext.getBusinessId();
        Supplier supplier = supplierRepository.findByIdAndBusinessId(request.getSupplierId(), businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Supplier not found in this business"));
        authz.assertBusinessOwnership(supplier);

        Purchase purchase = new Purchase();
        purchase.setBusinessId(businessId);
        purchase.setSupplierId(supplier.getId());
        purchase.setInvoiceNumber(request.getInvoiceNumber());
        purchase.setPurchaseDate(request.getPurchaseDate());
        purchaseRepository.save(purchase);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        List<PurchaseItem> items = new ArrayList<>();
        for (PurchaseItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findByIdAndBusinessId(itemRequest.getProductId(), businessId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Product not found in this business"));
            authz.assertBusinessOwnership(product);

            BigDecimal quantity = itemRequest.getQuantity().setScale(4, RoundingMode.HALF_UP);
            BigDecimal unitCost = itemRequest.getUnitCost().setScale(4, RoundingMode.HALF_UP);
            BigDecimal lineNet = quantity.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
            BigDecimal tax = itemRequest.getTaxAmount() == null ? BigDecimal.ZERO
                    : itemRequest.getTaxAmount().setScale(4, RoundingMode.HALF_UP);

            PurchaseItem item = new PurchaseItem();
            item.setBusinessId(businessId);
            item.setPurchaseId(purchase.getId());
            item.setProductId(product.getId());
            item.setQuantity(quantity);
            item.setUnitCost(unitCost);
            item.setTaxAmount(tax);
            item.setLineTotal(lineNet.add(tax));
            items.add(purchaseItemRepository.save(item));
            subtotal = subtotal.add(lineNet);
            taxTotal = taxTotal.add(tax);
        }

        purchase.setSubtotal(subtotal);
        purchase.setTaxAmount(taxTotal);
        purchase.setTotalAmount(subtotal.add(taxTotal));
        purchaseRepository.save(purchase);
        return toResponse(purchase, items, null, null);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseResponse> list(Pageable pageable) {
        Page<Purchase> page = purchaseRepository.findAllByBusinessId(businessContext.getBusinessId(), pageable);

        // Blueprint rule 15: batch-fetch every item and supplier for the page in
        // one query each - a per-row repository call here is the classic N+1.
        List<Long> purchaseIds = new ArrayList<>();
        List<Long> supplierIds = new ArrayList<>();
        for (Purchase purchase : page.getContent()) {
            purchaseIds.add(purchase.getId());
            supplierIds.add(purchase.getSupplierId());
        }
        Map<Long, List<PurchaseItem>> itemsByPurchase = new HashMap<>();
        if (!purchaseIds.isEmpty()) {
            for (PurchaseItem item : purchaseItemRepository.findAllByPurchaseIdIn(purchaseIds)) {
                itemsByPurchase.computeIfAbsent(item.getPurchaseId(), key -> new ArrayList<>()).add(item);
            }
        }
        Map<Long, String> supplierNames = new HashMap<>();
        for (Supplier supplier : supplierRepository.findAllById(supplierIds)) {
            supplierNames.put(supplier.getId(), supplier.getName());
        }
        List<Long> allProductIds = new ArrayList<>();
        for (List<PurchaseItem> purchaseItems : itemsByPurchase.values()) {
            for (PurchaseItem item : purchaseItems) {
                allProductIds.add(item.getProductId());
            }
        }
        Map<Long, String> productNames = new HashMap<>();
        for (Product product : productRepository.findAllById(allProductIds)) {
            productNames.put(product.getId(), product.getName());
        }

        return page.map(purchase -> {
            List<PurchaseItem> items = itemsByPurchase.getOrDefault(purchase.getId(), new ArrayList<>());
            return toResponse(purchase, items, supplierNames.get(purchase.getSupplierId()), productNames);
        });
    }

    /**
     * Receiving: per item, the WAC recalculation locks and updates the balance
     * row and writes the movement (all inside this transaction); then one
     * journal entry recognizes the inventory. Unpaid receipts credit AP 2000;
     * an already-fully-paid purchase credits bank 1010 instead (rare path -
     * payment-after-receipt is the normal flow).
     */
    @Transactional
    public PurchaseResponse receive(Long purchaseId) {
        Purchase purchase = loadOwned(purchaseId);
        if (purchase.getStatus() != Purchase.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only a DRAFT purchase can be received; this purchase is " + purchase.getStatus());
        }
        List<PurchaseItem> items = purchaseItemRepository.findAllByPurchaseId(purchaseId);
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot receive an empty purchase");
        }

        Long userId = SecurityUtils.currentUserId();
        for (PurchaseItem item : items) {
            inventoryService.recordReceipt(purchase.getBusinessId(), item, userId);
        }

        boolean alreadyPaid = purchase.getAmountPaid().compareTo(purchase.getTotalAmount()) >= 0;
        String creditAccount = alreadyPaid ? "1010" : "2000";
        List<LineSpec> lines = new ArrayList<>();
        lines.add(new LineSpec("1200", purchase.getSubtotal(), BigDecimal.ZERO, "Inventory received"));
        if (purchase.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Input VAT is not inventoriable cost: it debits the VAT control
            // account via the same code used for output tax, netted by sign.
            lines.add(new LineSpec("2300", purchase.getTaxAmount(), BigDecimal.ZERO, "Input VAT"));
        }
        lines.add(new LineSpec(creditAccount, BigDecimal.ZERO, purchase.getTotalAmount(),
                alreadyPaid ? "Paid supplier" : "Supplier payable"));
        postingService.post(purchase.getBusinessId(), "PURCHASE_RECEIPT", purchaseId, Instant.now(),
                "Purchase " + purchaseId + " received", lines, userId);

        purchase.setStatus(Purchase.Status.RECEIVED);
        purchaseRepository.save(purchase);
        return toResponse(purchase, items, null, null);
    }

    /**
     * Payment against the supplier balance: DR 2000 Accounts Payable /
     * CR 1000 cash or 1010 bank. Overpayment beyond the outstanding balance
     * is rejected; reaching zero flips the purchase to PAID.
     */
    @Transactional
    public PurchaseResponse pay(Long purchaseId, PayPurchaseRequest request) {
        Purchase purchase = loadOwned(purchaseId);
        if (purchase.getStatus() != Purchase.Status.RECEIVED && purchase.getStatus() != Purchase.Status.PAID) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only a received purchase can be paid; this purchase is " + purchase.getStatus());
        }
        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);
        BigDecimal outstanding = purchase.getTotalAmount().subtract(purchase.getAmountPaid());
        if (amount.compareTo(outstanding) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Payment exceeds the outstanding balance " + outstanding);
        }

        String creditAccount = "CASH".equalsIgnoreCase(request.getPaymentMethod()) ? "1000" : "1010";
        List<LineSpec> lines = new ArrayList<>();
        lines.add(new LineSpec("2000", amount, BigDecimal.ZERO, "Supplier payable settled"));
        lines.add(new LineSpec(creditAccount, BigDecimal.ZERO, amount, "Payment to supplier"));
        postingService.post(purchase.getBusinessId(), "PURCHASE_PAYMENT", purchaseId, Instant.now(),
                "Purchase " + purchaseId + " payment", lines, SecurityUtils.currentUserId());

        purchase.setAmountPaid(purchase.getAmountPaid().add(amount));
        if (purchase.getAmountPaid().compareTo(purchase.getTotalAmount()) >= 0) {
            purchase.setStatus(Purchase.Status.PAID);
        }
        purchaseRepository.save(purchase);
        return toResponse(purchase, purchaseItemRepository.findAllByPurchaseId(purchaseId), null, null);
    }

    @Transactional(readOnly = true)
    public PurchaseResponse getById(Long id) {
        Purchase purchase = loadOwned(id);
        return toResponse(purchase, purchaseItemRepository.findAllByPurchaseId(purchase.getId()), null, null);
    }

    private Purchase loadOwned(Long id) {
        Purchase purchase = purchaseRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase not found"));
        authz.assertBusinessOwnership(purchase);
        return purchase;
    }

    private PurchaseResponse toResponse(Purchase purchase, List<PurchaseItem> items, String supplierName,
                                          Map<Long, String> sharedProductNames) {
        PurchaseResponse response = new PurchaseResponse();
        response.setId(purchase.getId());
        response.setSupplierId(purchase.getSupplierId());
        if (supplierName != null) {
            response.setSupplierName(supplierName);
        } else {
            supplierRepository.findById(purchase.getSupplierId())
                    .ifPresent(supplier -> response.setSupplierName(supplier.getName()));
        }
        response.setInvoiceNumber(purchase.getInvoiceNumber());
        response.setStatus(purchase.getStatus().name());
        response.setSubtotal(purchase.getSubtotal());
        response.setTaxAmount(purchase.getTaxAmount());
        response.setTotalAmount(purchase.getTotalAmount());
        response.setAmountPaid(purchase.getAmountPaid());
        response.setPurchaseDate(purchase.getPurchaseDate());

        Map<Long, String> productNames = sharedProductNames;
        if (productNames == null) {
            List<Long> productIds = new ArrayList<>();
            for (PurchaseItem item : items) {
                productIds.add(item.getProductId());
            }
            productNames = new HashMap<>();
            for (Product product : productRepository.findAllById(productIds)) {
                productNames.put(product.getId(), product.getName());
            }
        }

        List<PurchaseItemResponse> itemResponses = new ArrayList<>();
        for (PurchaseItem item : items) {
            PurchaseItemResponse itemResponse = new PurchaseItemResponse();
            itemResponse.setId(item.getId());
            itemResponse.setProductId(item.getProductId());
            itemResponse.setProductName(productNames.get(item.getProductId()));
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setUnitCost(item.getUnitCost());
            itemResponse.setTaxAmount(item.getTaxAmount());
            itemResponse.setLineTotal(item.getLineTotal());
            itemResponses.add(itemResponse);
        }
        response.setItems(itemResponses);
        return response;
    }

    // Kept for the receive/pay posting-date seam: purchases post on the
    // business-local date of the event, same rule as the sale ledger.
    @SuppressWarnings("unused")
    private LocalDate businessToday(Long businessId) {
        return LocalDate.now(ZoneId.of(businessRepository.findById(businessId).orElseThrow().getTimezone()));
    }
}
