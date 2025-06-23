package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.ProductCreationRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.Supplier;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.ProductMapper;
import com.bacpham.kanban_service.repository.CategoryRepository;
import com.bacpham.kanban_service.repository.ProductRepository;
import com.bacpham.kanban_service.repository.SubProductRepository;
import com.bacpham.kanban_service.repository.SupplierRepository;
import com.bacpham.kanban_service.service.IProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProductServiceImpl implements IProductService {
    ProductRepository productRepository;
    ProductMapper productMapper;
    CategoryRepository categoryRepository;
    SupplierRepository supplierRepository;

    public ProductResponse createProduct(ProductCreationRequest request) {
        log.info("Creating product with request: {}", request.toString());
        Product product = productMapper.toProduct(request);
        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            Set<Category> categories = new HashSet<>(categoryRepository.findAllById(request.getCategories()));
            product.setCategories(categories);
        }

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));

        product.setSupplier(supplier);

        product = productRepository.save(product);

        return productMapper.toProductResponse(product);
    }


    public List<ProductResponse> getProducts() {
        return productRepository.findAll().stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }
//    @Cacheable(value = "productsPage", key = "'page_'+#page+'_'+#pageSize+'_'+#title", unless = "#result == null")
    public PageResponse<ProductResponse> getProductPage(int page, int pageSize, String title) {
        log.info("Retrieving product page: page={}, pageSize={}, title={}", page, pageSize, title);
        Sort sort = Sort.by("createdAt").descending();

        if (title != null && !title.isEmpty()) {
            page = 1;
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        Page<Product> pageData;

        if (title != null && !title.isEmpty()) {
            pageData = productRepository.findByTitleContainingIgnoreCase(title, pageable);
        } else {
            pageData = productRepository.findAllByDeletedFalse(pageable);
        }

        return PageResponse.<ProductResponse>builder()
                .currentPage(page)
                .pageSize(pageData.getSize())
                .totalPages(pageData.getTotalPages())
                .totalElements(pageData.getTotalElements())
                .data(pageData.getContent().stream().map(productMapper::toProductResponse).toList())
                .build();
    }

    //delete product by id
    public void deleteProduct(String id) {

      Product product =   productRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

      product.setDeleted(true);

      productRepository.save(product);

    }

    public ProductResponse getProductById(String slug, String id) {
log.info("Retrieving product with id: {}", id);
        Product product = productRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return productMapper.toProductResponse(product);


    }

    public ProductResponse updateProduct(String id, ProductCreationRequest request) {
        log.info("Updating product id: {}", id);
        Product product = productRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        productMapper.updateProduct(product, request);

        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            Set<Category> categories = new HashSet<>(categoryRepository.findAllById(request.getCategories()));
            product.setCategories(categories);
        }

        product = productRepository.save(product);

        return productMapper.toProductResponse(product);
    }

    public Page<ProductResponse> getFilteredProducts(
            List<String> categoryIds,
            List<String> sizes,
            List<String> colors,
            List<Double> priceRange,
            Pageable pageable
    ) {
        if (categoryIds != null && categoryIds.isEmpty()) {
            categoryIds = null;
        }
        if (sizes != null && sizes.isEmpty()) {
            sizes = null;
        }
        if (colors != null && colors.isEmpty()) {
            colors = null;
        }

        Double minPrice = (priceRange != null && !priceRange.isEmpty()) ? priceRange.get(0) : null;
        Double maxPrice = (priceRange != null && priceRange.size() > 1) ? priceRange.get(1) : null;

        Page<Product> filteredProductsPage = productRepository.findFilteredProducts(
                categoryIds,
                sizes,
                colors,
                minPrice,
                maxPrice,
                pageable
        );
        log.info("Filtered products page: {}", filteredProductsPage);

        return filteredProductsPage.map(productMapper::toProductResponse);
    }
}