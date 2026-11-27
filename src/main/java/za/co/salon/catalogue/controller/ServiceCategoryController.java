package za.co.salon.catalogue.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.salon.catalogue.dto.ServiceCategoryRequest;
import za.co.salon.catalogue.dto.ServiceCategoryResponse;
import za.co.salon.catalogue.service.ServiceCatalogueService;

@RestController
@RequestMapping("/api/v1/service-categories")
public class ServiceCategoryController {

    private final ServiceCatalogueService catalogueService;

    public ServiceCategoryController(ServiceCatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @PostMapping
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public ResponseEntity<ServiceCategoryResponse> create(@Valid @RequestBody ServiceCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogueService.createCategory(request));
    }

    @GetMapping
    @PreAuthorize("@authz.requireMembership()")
    public Page<ServiceCategoryResponse> list(Pageable pageable) {
        return catalogueService.listCategories(pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public ServiceCategoryResponse update(@PathVariable Long id, @Valid @RequestBody ServiceCategoryRequest request) {
        return catalogueService.updateCategory(id, request);
    }
}
