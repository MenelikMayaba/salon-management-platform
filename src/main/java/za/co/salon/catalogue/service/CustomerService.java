package za.co.salon.catalogue.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.catalogue.dto.CustomerRequest;
import za.co.salon.catalogue.dto.CustomerResponse;
import za.co.salon.catalogue.entity.Customer;
import za.co.salon.catalogue.repository.CustomerRepository;
import za.co.salon.shared.security.BusinessAuthorizationService;
import za.co.salon.shared.security.BusinessContext;

/**
 * Customer CRUD. Every read and write is scoped to the active business;
 * a foreign-tenant id resolves to 404, never to data.
 */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final BusinessAuthorizationService authz;
    private final BusinessContext businessContext;

    public CustomerService(CustomerRepository customerRepository,
                           BusinessAuthorizationService authz,
                           BusinessContext businessContext) {
        this.customerRepository = customerRepository;
        this.authz = authz;
        this.businessContext = businessContext;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setBusinessId(businessContext.getBusinessId());
        applyRequest(customer, request);
        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(Pageable pageable) {
        return customerRepository.findAllByBusinessId(businessContext.getBusinessId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        return toResponse(loadOwned(id));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = loadOwned(id);
        applyRequest(customer, request);
        return toResponse(customerRepository.save(customer));
    }

    private Customer loadOwned(Long id) {
        Customer customer = customerRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        authz.assertBusinessOwnership(customer);
        return customer;
    }

    private void applyRequest(Customer customer, CustomerRequest request) {
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setNotes(request.getNotes());
        customer.setActive(request.isActive());
    }

    private CustomerResponse toResponse(Customer customer) {
        CustomerResponse response = new CustomerResponse();
        response.setId(customer.getId());
        response.setFirstName(customer.getFirstName());
        response.setLastName(customer.getLastName());
        response.setEmail(customer.getEmail());
        response.setPhoneNumber(customer.getPhoneNumber());
        response.setNotes(customer.getNotes());
        response.setActive(customer.isActive());
        return response;
    }
}
