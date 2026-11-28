package za.co.salon.inventory.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.inventory.dto.SupplierRequest;
import za.co.salon.inventory.dto.SupplierResponse;
import za.co.salon.inventory.entity.Supplier;
import za.co.salon.inventory.repository.SupplierRepository;
import za.co.salon.shared.security.BusinessAuthorizationService;
import za.co.salon.shared.security.BusinessContext;

/**
 * Supplier CRUD. Same conventions as the catalogue module: thin service,
 * ownership assertions on every load by external id, soft state via update.
 */
@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final BusinessAuthorizationService authz;
    private final BusinessContext businessContext;

    public SupplierService(SupplierRepository supplierRepository,
                           BusinessAuthorizationService authz,
                           BusinessContext businessContext) {
        this.supplierRepository = supplierRepository;
        this.authz = authz;
        this.businessContext = businessContext;
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Supplier supplier = new Supplier();
        supplier.setBusinessId(businessContext.getBusinessId());
        applyRequest(supplier, request);
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> list(Pageable pageable) {
        return supplierRepository.findAllByBusinessId(businessContext.getBusinessId(), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = loadOwned(id);
        applyRequest(supplier, request);
        return toResponse(supplierRepository.save(supplier));
    }

    private Supplier loadOwned(Long id) {
        Supplier supplier = supplierRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        authz.assertBusinessOwnership(supplier);
        return supplier;
    }

    private void applyRequest(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.getName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setEmail(request.getEmail());
        supplier.setPhoneNumber(request.getPhoneNumber());
        supplier.setAddress(request.getAddress());
    }

    private SupplierResponse toResponse(Supplier supplier) {
        SupplierResponse response = new SupplierResponse();
        response.setId(supplier.getId());
        response.setName(supplier.getName());
        response.setContactPerson(supplier.getContactPerson());
        response.setEmail(supplier.getEmail());
        response.setPhoneNumber(supplier.getPhoneNumber());
        response.setAddress(supplier.getAddress());
        return response;
    }
}
