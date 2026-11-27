package za.co.salon.sales.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.catalogue.entity.Product;
import za.co.salon.catalogue.entity.TaxCode;
import za.co.salon.catalogue.repository.ProductRepository;
import za.co.salon.catalogue.repository.ServiceRepository;
import za.co.salon.catalogue.repository.TaxCodeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.salon.sales.dto.DraftSaleRequest;
import za.co.salon.sales.dto.PaymentRequest;
import za.co.salon.sales.dto.PaymentResponse;
import za.co.salon.sales.dto.SaleItemRequest;
import za.co.salon.sales.dto.SaleItemResponse;
import za.co.salon.sales.dto.SaleResponse;
import za.co.salon.sales.entity.Payment;
import za.co.salon.sales.entity.Sale;
import za.co.salon.sales.entity.SaleItem;
import za.co.salon.sales.repository.PaymentRepository;
import za.co.salon.sales.repository.SaleItemRepository;
import za.co.salon.sales.repository.SaleRepository;
import za.co.salon.shared.security.BusinessAuthorizationService;
import za.co.salon.shared.security.BusinessContext;
import za.co.salon.business.repository.BusinessRepository;
import za.co.salon.finance.accounting.SequenceNumberService;
import za.co.salon.finance.entity.JournalEntry;
import za.co.salon.finance.entity.JournalEntryLine;
import za.co.salon.finance.repository.AccountRepository;
import za.co.salon.finance.repository.JournalEntryLineRepository;
import za.co.salon.finance.repository.JournalEntryRepository;
import za.co.salon.finance.posting.JournalPostingService;
import za.co.salon.finance.posting.LineSpec;
import za.co.salon.inventory.service.InventoryService;
import za.co.salon.sales.spi.CommissionService;
import za.co.salon.sales.dto.RefundRequest;
import za.co.salon.sales.dto.RefundResponse;
import za.co.salon.sales.entity.Refund;
import za.co.salon.sales.repository.RefundRepository;
import za.co.salon.shared.security.SecurityUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Sales lifecycle. Draft creation snapshots every catalogue value - description,
 * price, tax - so a receipt never changes when the catalogue does. Totals use
 * scale-4 HALF_UP at every aggregation boundary (blueprint rule 1: no doubles).
 *
 * Pricing decision (open §12 question, documented per phase doc): catalogue
 * prices are TAX-EXCLUSIVE; tax is added on top at the line level.
 */
@Service
public class SaleService {

    private static final int MONEY_SCALE = 4;

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;
    private final SequenceNumberService sequenceNumberService;
    private final JournalPostingService postingService;
    private final InventoryService inventoryService;
    private final CommissionService commissionService;
    private final RefundRepository refundRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;
    private final AccountRepository accountRepository;
    private final BusinessRepository businessRepository;
    private final ServiceRepository serviceRepository;
    private final ProductRepository productRepository;
    private final TaxCodeRepository taxCodeRepository;
    private final BusinessAuthorizationService authz;
    private final BusinessContext businessContext;

    public SaleService(SaleRepository saleRepository,
                       SaleItemRepository saleItemRepository,
                       PaymentRepository paymentRepository,
                       SequenceNumberService sequenceNumberService,
                       JournalPostingService postingService,
                       InventoryService inventoryService,
                       CommissionService commissionService,
                       RefundRepository refundRepository,
                       JournalEntryRepository journalEntryRepository,
                       JournalEntryLineRepository journalEntryLineRepository,
                       AccountRepository accountRepository,
                       BusinessRepository businessRepository,
                       ServiceRepository serviceRepository,
                       ProductRepository productRepository,
                       TaxCodeRepository taxCodeRepository,
                       BusinessAuthorizationService authz,
                       BusinessContext businessContext) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.paymentRepository = paymentRepository;
        this.sequenceNumberService = sequenceNumberService;
        this.postingService = postingService;
        this.inventoryService = inventoryService;
        this.commissionService = commissionService;
        this.refundRepository = refundRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalEntryLineRepository = journalEntryLineRepository;
        this.accountRepository = accountRepository;
        this.businessRepository = businessRepository;
        this.serviceRepository = serviceRepository;
        this.productRepository = productRepository;
        this.taxCodeRepository = taxCodeRepository;
        this.authz = authz;
        this.businessContext = businessContext;
    }

    @Transactional
    public SaleResponse createDraft(DraftSaleRequest request) {
        Long businessId = businessContext.getBusinessId();
        Sale sale = new Sale();
        sale.setBusinessId(businessId);
        sale.setCustomerId(request.getCustomerId());
        sale.setCreatedBy(SecurityUtils.currentUserId());

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        List<SaleItem> items = new ArrayList<>();
        for (SaleItemRequest itemRequest : request.getItems()) {
            SaleItem item = buildItem(businessId, itemRequest);
            items.add(item);
            BigDecimal gross = item.getQuantity().multiply(item.getUnitPrice());
            subtotal = subtotal.add(gross);
            discountTotal = discountTotal.add(item.getDiscountAmount());
            taxTotal = taxTotal.add(item.getTaxAmount());
        }

        sale.setSubtotal(scale(subtotal));
        sale.setDiscountAmount(scale(discountTotal));
        sale.setTaxAmount(scale(taxTotal));
        sale.setTotalAmount(scale(subtotal.subtract(discountTotal).add(taxTotal)));
        saleRepository.save(sale);

        for (SaleItem item : items) {
            item.setSaleId(sale.getId());
            saleItemRepository.save(item);
        }
        return toResponse(sale, items);
    }


    /**
     * Records a payment against a sale. Payments on a DRAFT are stored but only
     * become ledger-visible inside complete() - posting stays atomic with the
     * sale (documented decision per phase doc).
     *
     * Idempotency: the unique (business, idempotency_key) constraint is the
     * backstop; the pre-check is the fast path. A retried request returns the
     * original payment - a customer is never double-charged by a network retry.
     * All methods settle as SUCCESS immediately in this phase - there is no
     * payment gateway integration yet.
     */
    @Transactional
    public PaymentResponse addPayment(Long saleId, PaymentRequest request) {
        Long businessId = businessContext.getBusinessId();
        java.util.Optional<Payment> existing =
                paymentRepository.findByBusinessIdAndIdempotencyKey(businessId, request.getIdempotencyKey());
        if (existing.isPresent()) {
            return toPaymentResponse(existing.get());
        }

        Sale sale = loadOwned(saleId);
        if (sale.getStatus() == Sale.Status.VOIDED || sale.getStatus() == Sale.Status.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cannot take payment on a " + sale.getStatus() + " sale");
        }
        BigDecimal paidSoFar = BigDecimal.ZERO;
        for (Payment payment : paymentRepository.findAllBySaleId(saleId)) {
            if (payment.getStatus() == Payment.Status.SUCCESS) {
                paidSoFar = paidSoFar.add(payment.getAmount());
            }
        }
        BigDecimal outstanding = sale.getTotalAmount().subtract(paidSoFar);
        if (request.getAmount().compareTo(outstanding) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Payment " + request.getAmount() + " exceeds outstanding balance " + outstanding);
        }

        Payment payment = new Payment();
        payment.setBusinessId(businessId);
        payment.setSaleId(sale.getId());
        payment.setPaymentMethod(parsePaymentMethod(request.getPaymentMethod()));
        payment.setAmount(request.getAmount());
        payment.setReferenceNumber(request.getReferenceNumber());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setStatus(Payment.Status.SUCCESS);
        payment.setReceivedAt(java.time.Instant.now());
        try {
            return toPaymentResponse(paymentRepository.saveAndFlush(payment));
        } catch (DataIntegrityViolationException duplicateKey) {
            // Lost the race against a concurrent request with the same key -
            // the winner's row is the correct response for both.
            return paymentRepository.findByBusinessIdAndIdempotencyKey(businessId, request.getIdempotencyKey())
                    .map(this::toPaymentResponse)
                    .orElseThrow(() -> duplicateKey);
        }
    }

    private Payment.Method parsePaymentMethod(String value) {
        try {
            return Payment.Method.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown payment method: " + value + " (valid: CASH, CARD, EFT, OTHER)");
        }
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setPaymentMethod(payment.getPaymentMethod().name());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus().name());
        response.setReferenceNumber(payment.getReferenceNumber());
        response.setIdempotencyKey(payment.getIdempotencyKey());
        response.setReceivedAt(payment.getReceivedAt());
        return response;
    }

    /**
     * The blueprint 8.1 workflow - the single biggest transaction in the system.
     * Counter allocation, sale completion, stock deduction, movement audit,
     * commission accrual and three journal entries commit or roll back TOGETHER.
     *
     * Ordering contract: everything is validated before the first write, so a
     * validation failure leaves the transaction untouched. Stock balances lock
     * in sorted product-id order (deadlock mitigation). The sale number is
     * consumed inside this transaction - an abort returns it to the pool.
     */
    @Transactional
    public SaleResponse complete(Long saleId) {
        Long businessId = businessContext.getBusinessId();
        Sale sale = loadOwned(saleId);
        if (sale.getStatus() != Sale.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only a DRAFT sale can be completed; this sale is " + sale.getStatus());
        }
        List<SaleItem> items = saleItemRepository.findAllBySaleId(saleId);
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cannot complete a sale with no items");
        }

        // Product quantities grouped in sorted product-id order - deterministic
        // lock ordering for concurrent completions.
        java.util.TreeMap<Long, BigDecimal> quantitiesByProduct = new java.util.TreeMap<>();
        for (SaleItem item : items) {
            if (item.getItemType() == SaleItem.ItemType.PRODUCT) {
                quantitiesByProduct.merge(item.getProductId(), item.getQuantity(), BigDecimal::add);
            }
        }

        // WRITE PHASE - nothing above this line mutates anything.
        long saleNumber = sequenceNumberService.next(businessId, "sale");
        sale.setSaleNumber(String.valueOf(saleNumber));
        sale.setStatus(Sale.Status.COMPLETED);
        java.time.Instant completionInstant = java.time.Instant.now();
        sale.setCompletedAt(completionInstant);
        saleRepository.save(sale);

        java.util.Map<Long, BigDecimal> costByProduct = inventoryService.recordSaleMovements(
                businessId, saleId, quantitiesByProduct, sale.getCreatedBy());
        commissionService.recordFromSale(sale, items);

        postRecognitionEntry(sale, items, completionInstant);
        postCollectionEntry(sale, completionInstant);
        postCogsEntry(sale, costByProduct, completionInstant);
        return toResponse(sale, items);
    }

    /**
     * Voids a draft sale - the only sale status allowed to be discarded.
     * A completed sale is immutable financial history; refund is its only
     * correction path.
     */
    @Transactional
    public SaleResponse voidSale(Long saleId) {
        Sale sale = loadOwned(saleId);
        if (sale.getStatus() != Sale.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only a DRAFT sale can be voided; this sale is " + sale.getStatus());
        }
        sale.setStatus(Sale.Status.VOIDED);
        saleRepository.save(sale);
        return toResponse(sale, saleItemRepository.findAllBySaleId(saleId));
    }

    /**
     * Refunds money against a completed sale and reverses the accounting -
     * never silent money movement. Full refund: the recognition entry is
     * reversed wholesale, which is why reversal is all-or-nothing by design.
     * Partial refund: a proportional contra-entry against each revenue stream
     * and the VAT line, because a partial reversal cannot exist.
     *
     * Documented phase 5 simplification: the cash/bank OUTFLOW side of the
     * refund is not yet posted (the blueprint does not specify refund method
     * capture); the revenue/AR reversal IS complete and exact.
     */
    @Transactional
    public RefundResponse refund(Long saleId, RefundRequest request) {
        Sale sale = loadOwned(saleId);
        if (sale.getStatus() != Sale.Status.COMPLETED && sale.getStatus() != Sale.Status.PARTIALLY_REFUNDED) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only a completed sale can be refunded; this sale is " + sale.getStatus());
        }
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Refund amount must be positive");
        }
        BigDecimal alreadyRefunded = BigDecimal.ZERO;
        for (Refund existing : refundRepository.findAllBySaleId(saleId)) {
            alreadyRefunded = alreadyRefunded.add(existing.getAmount());
        }
        BigDecimal cumulative = alreadyRefunded.add(amount);
        if (cumulative.compareTo(sale.getTotalAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Refund exceeds the sale total: already refunded " + alreadyRefunded);
        }

        Refund refund = new Refund();
        refund.setBusinessId(sale.getBusinessId());
        refund.setSaleId(saleId);
        refund.setAmount(amount.setScale(4, RoundingMode.HALF_UP));
        refund.setReason(request.getReason());
        refund.setRefundedBy(SecurityUtils.currentUserId());
        refund.setRefundedAt(java.time.Instant.now());
        refundRepository.save(refund);

        JournalEntry recognition = journalEntryRepository
                .findByBusinessIdAndSourceTypeAndSourceId(sale.getBusinessId(), "SALE", saleId)
                .orElseThrow(() -> new IllegalStateException("Completed sale without recognition entry"));
        java.time.LocalDate refundDate = java.time.LocalDate.now(
                java.time.ZoneId.of(businessRepository.findById(sale.getBusinessId()).orElseThrow().getTimezone()));

        if (cumulative.compareTo(sale.getTotalAmount()) == 0) {
            postingService.reverse(recognition.getId(), refundDate, "Full refund of sale " + sale.getSaleNumber());
            sale.setStatus(Sale.Status.REFUNDED);
        } else {
            postPartialRefundContraEntry(sale, recognition, amount, refund.getId(), refundDate);
            sale.setStatus(Sale.Status.PARTIALLY_REFUNDED);
        }
        saleRepository.save(sale);
        return new RefundResponse(refund.getId(), saleId, refund.getAmount(), refund.getReason(),
                refund.getStatus().name(), sale.getStatus().name());
    }

    /**
     * Partial refund contra-entry: each credit line of the original recognition
     * (revenue streams, VAT) is debited proportionally to the refund amount,
     * credit AR for the refund total. Largest line absorbs the rounding remainder
     * so the entry always balances exactly.
     */
    private void postPartialRefundContraEntry(Sale sale, JournalEntry recognition, BigDecimal amount,
                                              Long refundId, java.time.LocalDate refundDate) {
        List<JournalEntryLine> originalLines = journalEntryLineRepository.findAllByJournalEntryId(recognition.getId());
        List<JournalEntryLine> creditLines = new ArrayList<>();
        for (JournalEntryLine line : originalLines) {
            if (line.getCredit().compareTo(BigDecimal.ZERO) > 0) {
                creditLines.add(line);
            }
        }
        List<LineSpec> lines = new ArrayList<>();
        lines.add(new LineSpec("1100", BigDecimal.ZERO, amount, "Refund reduces receivable"));
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < creditLines.size(); i++) {
            JournalEntryLine original = creditLines.get(i);
            BigDecimal share;
            if (i == creditLines.size() - 1) {
                share = amount.subtract(allocated);
            } else {
                share = original.getCredit().multiply(amount)
                        .divide(sale.getTotalAmount(), 4, RoundingMode.HALF_UP);
                allocated = allocated.add(share);
            }
            if (share.compareTo(BigDecimal.ZERO) > 0) {
                za.co.salon.finance.entity.Account account = accountRepository.findById(original.getAccountId()).orElseThrow();
                lines.add(new LineSpec(account.getAccountCode(), share, BigDecimal.ZERO, "Partial refund"));
            }
        }
        postingService.post(sale.getBusinessId(), "REFUND", refundId, java.time.Instant.now(),
                "Partial refund of sale " + sale.getSaleNumber(), lines, sale.getCreatedBy());
    }

    /**
     * Sale recognition: DR 1100 Accounts Receivable for the total;
     * CR revenue accounts net of tax per stream; CR 2300 VAT Payable when taxed.
     * Zero-amount lines are omitted - the ledger never carries empty lines.
     */
    private void postRecognitionEntry(Sale sale, List<SaleItem> items, java.time.Instant when) {
        BigDecimal serviceNet = BigDecimal.ZERO;
        BigDecimal productNet = BigDecimal.ZERO;
        for (SaleItem item : items) {
            BigDecimal net = item.getLineTotal().subtract(item.getTaxAmount());
            if (item.getItemType() == SaleItem.ItemType.SERVICE) {
                serviceNet = serviceNet.add(net);
            } else {
                productNet = productNet.add(net);
            }
        }
        List<LineSpec> lines = new ArrayList<>();
        lines.add(new LineSpec("1100", sale.getTotalAmount(), BigDecimal.ZERO, "Accounts receivable"));
        if (serviceNet.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new LineSpec("4000", BigDecimal.ZERO, serviceNet, "Service revenue"));
        }
        if (productNet.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new LineSpec("4100", BigDecimal.ZERO, productNet, "Product sales revenue"));
        }
        if (sale.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new LineSpec("2300", BigDecimal.ZERO, sale.getTaxAmount(), "VAT payable"));
        }
        postingService.post(sale.getBusinessId(), "SALE", sale.getId(), when,
                "Sale " + sale.getSaleNumber() + " recognition", lines, sale.getCreatedBy());
    }

    /**
     * Payment collection: one DR per payment (CASH to 1000, everything else to
     * bank 1010), single CR to Accounts Receivable for the collected sum.
     * Skipped entirely when nothing was paid - the AR balance stays open.
     */
    private void postCollectionEntry(Sale sale, java.time.Instant when) {
        List<Payment> payments = paymentRepository.findAllBySaleId(sale.getId());
        List<LineSpec> lines = new ArrayList<>();
        BigDecimal collected = BigDecimal.ZERO;
        for (Payment payment : payments) {
            if (payment.getStatus() != Payment.Status.SUCCESS) {
                continue;
            }
            String debitAccount = payment.getPaymentMethod() == Payment.Method.CASH ? "1000" : "1010";
            lines.add(new LineSpec(debitAccount, payment.getAmount(), BigDecimal.ZERO,
                    payment.getPaymentMethod().name() + " payment"));
            collected = collected.add(payment.getAmount());
        }
        if (collected.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        lines.add(new LineSpec("1100", BigDecimal.ZERO, collected, "Receivable settled"));
        postingService.post(sale.getBusinessId(), "SALE_PAYMENTS", sale.getId(), when,
                "Sale " + sale.getSaleNumber() + " payment collection", lines, sale.getCreatedBy());
    }

    /**
     * Product cost recognition: DR 5000 COGS / CR 1200 Inventory at qty times
     * weighted average cost, read from the locked balances inside this transaction.
     * Skipped when cost is zero - nothing stocked, nothing to recognize.
     */
    private void postCogsEntry(Sale sale, java.util.Map<Long, BigDecimal> costByProduct, java.time.Instant when) {
        BigDecimal totalCost = BigDecimal.ZERO;
        for (BigDecimal cost : costByProduct.values()) {
            totalCost = totalCost.add(cost);
        }
        if (totalCost.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<LineSpec> lines = new ArrayList<>();
        lines.add(new LineSpec("5000", totalCost, BigDecimal.ZERO, "Cost of goods sold"));
        lines.add(new LineSpec("1200", BigDecimal.ZERO, totalCost, "Inventory"));
        postingService.post(sale.getBusinessId(), "SALE_COGS", sale.getId(), when,
                "Sale " + sale.getSaleNumber() + " cost of goods", lines, sale.getCreatedBy());
    }

    @Transactional(readOnly = true)
    public SaleResponse getById(Long id) {
        Sale sale = loadOwned(id);
        return toResponse(sale, saleItemRepository.findAllBySaleId(sale.getId()));
    }

    @Transactional(readOnly = true)
    public Page<SaleResponse> list(Pageable pageable) {
        // List view stays flat (no items) so query count cannot scale with rows.
        return saleRepository.findAllByBusinessId(businessContext.getBusinessId(), pageable)
                .map(sale -> toResponse(sale, null));
    }

    /**
     * Item shape: exactly one of service/product, matching itemType (the
     * service-layer replacement for the CHECK constraint auto-DDL cannot emit).
     * The catalogue row must resolve inside this business - a foreign-tenant
     * id is a validation failure, never a silent attach.
     */
    private SaleItem buildItem(Long businessId, SaleItemRequest request) {
        SaleItem.ItemType itemType = parseItemType(request.getItemType());
        validateItemShape(itemType, request);

        SaleItem item = new SaleItem();
        item.setBusinessId(businessId);
        item.setItemType(itemType);
        item.setEmployeeId(request.getEmployeeId());
        item.setQuantity(request.getQuantity());
        item.setDiscountAmount(request.getDiscountAmount() == null
                ? BigDecimal.ZERO : request.getDiscountAmount());

        Long taxCodeId;
        if (itemType == SaleItem.ItemType.SERVICE) {
            za.co.salon.catalogue.entity.Service service =
                    serviceRepository.findByIdAndBusinessId(request.getServiceId(), businessId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                    "Service does not exist in this business"));
            item.setServiceId(service.getId());
            item.setDescriptionSnapshot(service.getName());
            item.setUnitPrice(service.getPrice());
            taxCodeId = service.getTaxCodeId();
        } else {
            Product product = productRepository.findByIdAndBusinessId(request.getProductId(), businessId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Product does not exist in this business"));
            item.setProductId(product.getId());
            item.setDescriptionSnapshot(product.getName());
            item.setUnitPrice(product.getSellingPrice());
            taxCodeId = product.getTaxCodeId();
        }

        BigDecimal base = item.getQuantity().multiply(item.getUnitPrice()).subtract(item.getDiscountAmount());
        BigDecimal taxAmount = computeTax(businessId, taxCodeId, base);
        item.setTaxAmount(taxAmount);
        item.setLineTotal(scale(base.add(taxAmount)));
        return item;
    }

    private void validateItemShape(SaleItem.ItemType itemType, SaleItemRequest request) {
        boolean hasService = request.getServiceId() != null;
        boolean hasProduct = request.getProductId() != null;
        if (hasService == hasProduct) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Exactly one of serviceId or productId must be set");
        }
        if (itemType == SaleItem.ItemType.SERVICE && !hasService) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A SERVICE item must reference serviceId");
        }
        if (itemType == SaleItem.ItemType.PRODUCT && !hasProduct) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "A PRODUCT item must reference productId");
        }
    }

    /**
     * Tax-exclusive interpretation: tax = base x rate/100, snapshotted at draft.
     * Items without a tax code carry zero tax - not every salon is VAT-registered.
     */
    private BigDecimal computeTax(Long businessId, Long taxCodeId, BigDecimal base) {
        if (taxCodeId == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        TaxCode taxCode = taxCodeRepository.findByIdAndBusinessId(taxCodeId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tax code does not exist in this business"));
        BigDecimal tax = base.multiply(taxCode.getRate())
                .divide(new BigDecimal("100"), MONEY_SCALE, RoundingMode.HALF_UP);
        return scale(tax);
    }

    private SaleItem.ItemType parseItemType(String value) {
        try {
            return SaleItem.ItemType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown item type: " + value + " (valid: SERVICE, PRODUCT)");
        }
    }

    private Sale loadOwned(Long id) {
        Sale sale = saleRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale not found"));
        authz.assertBusinessOwnership(sale);
        return sale;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private SaleResponse toResponse(Sale sale, List<SaleItem> items) {
        SaleResponse response = new SaleResponse();
        response.setId(sale.getId());
        response.setCustomerId(sale.getCustomerId());
        response.setSaleNumber(sale.getSaleNumber());
        response.setStatus(sale.getStatus().name());
        response.setSubtotal(sale.getSubtotal());
        response.setDiscountAmount(sale.getDiscountAmount());
        response.setTaxAmount(sale.getTaxAmount());
        response.setTotalAmount(sale.getTotalAmount());
        response.setCompletedAt(sale.getCompletedAt());
        if (items != null) {
            List<SaleItemResponse> itemResponses = new ArrayList<>();
            for (SaleItem item : items) {
                itemResponses.add(toItemResponse(item));
            }
            response.setItems(itemResponses);
        }
        return response;
    }

    private SaleItemResponse toItemResponse(SaleItem item) {
        SaleItemResponse response = new SaleItemResponse();
        response.setId(item.getId());
        response.setItemType(item.getItemType().name());
        response.setServiceId(item.getServiceId());
        response.setProductId(item.getProductId());
        response.setEmployeeId(item.getEmployeeId());
        response.setDescriptionSnapshot(item.getDescriptionSnapshot());
        response.setQuantity(item.getQuantity());
        response.setUnitPrice(item.getUnitPrice());
        response.setDiscountAmount(item.getDiscountAmount());
        response.setTaxAmount(item.getTaxAmount());
        response.setLineTotal(item.getLineTotal());
        return response;
    }
}
