package za.co.salon.catalogue.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.catalogue.dto.ServiceCategoryRequest;
import za.co.salon.catalogue.dto.ServiceCategoryResponse;
import za.co.salon.catalogue.dto.ServiceRequest;
import za.co.salon.catalogue.dto.ServiceResponse;
import za.co.salon.catalogue.entity.ServiceCategory;
import za.co.salon.catalogue.repository.ServiceCategoryRepository;
import za.co.salon.catalogue.repository.ServiceRepository;
import za.co.salon.catalogue.repository.TaxCodeRepository;
import za.co.salon.shared.security.BusinessAuthorizationService;
import za.co.salon.shared.security.BusinessContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service catalogue: categories and services. Cross-entity validation loads the
 * referenced category/tax code scoped to the current business - a foreign-tenant
 * id fails validation (422), it is never silently attached.
 */
@Service
public class ServiceCatalogueService {

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceRepository serviceRepository;
    private final TaxCodeRepository taxCodeRepository;
    private final BusinessAuthorizationService authz;
    private final BusinessContext businessContext;

    public ServiceCatalogueService(ServiceCategoryRepository categoryRepository,
                                   ServiceRepository serviceRepository,
                                   TaxCodeRepository taxCodeRepository,
                                   BusinessAuthorizationService authz,
                                   BusinessContext businessContext) {
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.taxCodeRepository = taxCodeRepository;
        this.authz = authz;
        this.businessContext = businessContext;
    }

    @Transactional
    public ServiceCategoryResponse createCategory(ServiceCategoryRequest request) {
        ServiceCategory category = new ServiceCategory();
        category.setBusinessId(businessContext.getBusinessId());
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setActive(request.isActive());
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public Page<ServiceCategoryResponse> listCategories(Pageable pageable) {
        return categoryRepository.findAllByBusinessId(businessContext.getBusinessId(), pageable)
                .map(this::toCategoryResponse);
    }

    @Transactional
    public ServiceCategoryResponse updateCategory(Long id, ServiceCategoryRequest request) {
        ServiceCategory category = categoryRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service category not found"));
        authz.assertBusinessOwnership(category);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setActive(request.isActive());
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public ServiceResponse createService(ServiceRequest request) {
        za.co.salon.catalogue.entity.Service service = new za.co.salon.catalogue.entity.Service();
        service.setBusinessId(businessContext.getBusinessId());
        applyServiceRequest(service, request);
        return toServiceResponse(serviceRepository.save(service), categoryNameOf(service));
    }

    @Transactional(readOnly = true)
    public Page<ServiceResponse> listServices(Pageable pageable) {
        Page<za.co.salon.catalogue.entity.Service> page =
                serviceRepository.findAllByBusinessId(businessContext.getBusinessId(), pageable);
        Map<Long, String> categoryNames = batchFetchCategoryNames(page);
        return page.map(service -> toServiceResponse(service, categoryNames.get(service.getCategoryId())));
    }

    @Transactional
    public ServiceResponse updateService(Long id, ServiceRequest request) {
        za.co.salon.catalogue.entity.Service service =
                serviceRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        authz.assertBusinessOwnership(service);
        applyServiceRequest(service, request);
        return toServiceResponse(serviceRepository.save(service), categoryNameOf(service));
    }

    private void applyServiceRequest(za.co.salon.catalogue.entity.Service service, ServiceRequest request) {
        validateCategoryBelongsToBusiness(request.getCategoryId());
        validateTaxCodeBelongsToBusiness(request.getTaxCodeId());
        service.setCategoryId(request.getCategoryId());
        service.setName(request.getName());
        service.setDescription(request.getDescription());
        service.setDurationMinutes(request.getDurationMinutes());
        service.setPrice(request.getPrice());
        service.setTaxCodeId(request.getTaxCodeId());
        service.setActive(request.isActive());
    }

    private void validateCategoryBelongsToBusiness(Long categoryId) {
        if (categoryRepository.findByIdAndBusinessId(categoryId, businessContext.getBusinessId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Service category does not exist in this business");
        }
    }

    private void validateTaxCodeBelongsToBusiness(Long taxCodeId) {
        if (taxCodeId == null) {
            return;
        }
        if (taxCodeRepository.findByIdAndBusinessId(taxCodeId, businessContext.getBusinessId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tax code does not exist in this business");
        }
    }

    /**
     * Blueprint rule 15: never a repository call inside a loop. Collect the distinct
     * category ids for the page, fetch them in one query, and resolve names in memory.
     */
    private Map<Long, String> batchFetchCategoryNames(Page<za.co.salon.catalogue.entity.Service> page) {
        Set<Long> categoryIds = new HashSet<>();
        for (za.co.salon.catalogue.entity.Service service : page.getContent()) {
            categoryIds.add(service.getCategoryId());
        }
        Map<Long, String> names = new HashMap<>();
        for (ServiceCategory category : categoryRepository.findAllById(categoryIds)) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    private String categoryNameOf(za.co.salon.catalogue.entity.Service service) {
        return categoryRepository.findById(service.getCategoryId())
                .map(ServiceCategory::getName)
                .orElse(null);
    }

    private ServiceCategoryResponse toCategoryResponse(ServiceCategory category) {
        ServiceCategoryResponse response = new ServiceCategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setActive(category.isActive());
        return response;
    }

    private ServiceResponse toServiceResponse(za.co.salon.catalogue.entity.Service service, String categoryName) {
        ServiceResponse response = new ServiceResponse();
        response.setId(service.getId());
        response.setCategoryId(service.getCategoryId());
        response.setCategoryName(categoryName);
        response.setName(service.getName());
        response.setDescription(service.getDescription());
        response.setDurationMinutes(service.getDurationMinutes());
        response.setPrice(service.getPrice());
        response.setTaxCodeId(service.getTaxCodeId());
        response.setActive(service.isActive());
        return response;
    }
}
