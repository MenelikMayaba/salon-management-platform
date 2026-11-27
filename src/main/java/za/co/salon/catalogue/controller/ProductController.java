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
import za.co.salon.catalogue.dto.ProductRequest;
import za.co.salon.catalogue.dto.ProductResponse;
import za.co.salon.catalogue.service.ProductCatalogueService;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductCatalogueService catalogueService;

    public ProductController(ProductCatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @PostMapping
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogueService.createProduct(request));
    }

    @GetMapping
    @PreAuthorize("@authz.requireMembership()")
    public Page<ProductResponse> list(Pageable pageable) {
        return catalogueService.listProducts(pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.hasRole('OWNER','MANAGER')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return catalogueService.updateProduct(id, request);
    }
}
