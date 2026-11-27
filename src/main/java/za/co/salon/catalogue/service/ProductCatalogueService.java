package za.co.salon.catalogue.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.salon.catalogue.dto.ProductCategoryRequest;
import za.co.salon.catalogue.dto.ProductCategoryResponse;
import za.co.salon.catalogue.dto.ProductRequest;
import za.co.salon.catalogue.dto.ProductResponse;
import za.co.salon.catalogue.entity.Product;
import za.co.salon.catalogue.entity.ProductCategory;
import za.co.salon.catalogue.repository.ProductCategoryRepository;
import za.co.salon.catalogue.repository.ProductRepository;
import za.co.salon.catalogue.repository.TaxCodeRepository;
import za.co.salon.shared.security.BusinessAuthorizationService;
import za.co.salon.shared.security.BusinessContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Product catalogue: categories and products. Same cross-tenant validation and
 * batch-fetch patterns as the service catalogue.
 */
@Service
public class ProductCatalogueService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final TaxCodeRepository taxCodeRepository;
    private final BusinessAuthorizationService authz;
    private final BusinessContext businessContext;

    public ProductCatalogueService(ProductCategoryRepository categoryRepository,
                                   ProductRepository productRepository,
                                   TaxCodeRepository taxCodeRepository,
                                   BusinessAuthorizationService authz,
                                   BusinessContext businessContext) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.taxCodeRepository = taxCodeRepository;
        this.authz = authz;
        this.businessContext = businessContext;
    }

    @Transactional
    public ProductCategoryResponse createCategory(ProductCategoryRequest request) {
        ProductCategory category = new ProductCategory();
        category.setBusinessId(businessContext.getBusinessId());
        category.setName(request.getName());
        category.setActive(request.isActive());
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public Page<ProductCategoryResponse> listCategories(Pageable pageable) {
        return categoryRepository.findAllByBusinessId(businessContext.getBusinessId(), pageable)
                .map(this::toCategoryResponse);
    }

    @Transactional
    public ProductCategoryResponse updateCategory(Long id, ProductCategoryRequest request) {
        ProductCategory category = categoryRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product category not found"));
        authz.assertBusinessOwnership(category);
        category.setName(request.getName());
        category.setActive(request.isActive());
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product();
        product.setBusinessId(businessContext.getBusinessId());
        applyProductRequest(product, request);
        return toProductResponse(productRepository.save(product), categoryNameOf(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(Pageable pageable) {
        Page<Product> page = productRepository.findAllByBusinessId(businessContext.getBusinessId(), pageable);
        Map<Long, String> categoryNames = batchFetchCategoryNames(page);
        return page.map(product -> toProductResponse(product, categoryNames.get(product.getCategoryId())));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findByIdAndBusinessId(id, businessContext.getBusinessId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        authz.assertBusinessOwnership(product);
        applyProductRequest(product, request);
        return toProductResponse(productRepository.save(product), categoryNameOf(product));
    }

    private void applyProductRequest(Product product, ProductRequest request) {
        validateCategoryBelongsToBusiness(request.getCategoryId());
        validateTaxCodeBelongsToBusiness(request.getTaxCodeId());
        product.setCategoryId(request.getCategoryId());
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setBarcode(request.getBarcode());
        product.setSellingPrice(request.getSellingPrice());
        product.setReorderLevel(request.getReorderLevel());
        product.setTrackInventory(request.isTrackInventory());
        product.setTaxCodeId(request.getTaxCodeId());
        product.setActive(request.isActive());
    }

    private void validateCategoryBelongsToBusiness(Long categoryId) {
        if (categoryRepository.findByIdAndBusinessId(categoryId, businessContext.getBusinessId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Product category does not exist in this business");
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
     * Blueprint rule 15: batch-fetch category names in one query, never per row.
     */
    private Map<Long, String> batchFetchCategoryNames(Page<Product> page) {
        Set<Long> categoryIds = new HashSet<>();
        for (Product product : page.getContent()) {
            categoryIds.add(product.getCategoryId());
        }
        Map<Long, String> names = new HashMap<>();
        for (ProductCategory category : categoryRepository.findAllById(categoryIds)) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    private String categoryNameOf(Product product) {
        return categoryRepository.findById(product.getCategoryId())
                .map(ProductCategory::getName)
                .orElse(null);
    }

    private ProductCategoryResponse toCategoryResponse(ProductCategory category) {
        ProductCategoryResponse response = new ProductCategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setActive(category.isActive());
        return response;
    }

    private ProductResponse toProductResponse(Product product, String categoryName) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setCategoryId(product.getCategoryId());
        response.setCategoryName(categoryName);
        response.setName(product.getName());
        response.setSku(product.getSku());
        response.setBarcode(product.getBarcode());
        response.setSellingPrice(product.getSellingPrice());
        response.setReorderLevel(product.getReorderLevel());
        response.setTrackInventory(product.isTrackInventory());
        response.setTaxCodeId(product.getTaxCodeId());
        response.setActive(product.isActive());
        return response;
    }
}
