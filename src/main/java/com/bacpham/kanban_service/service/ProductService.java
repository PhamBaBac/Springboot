package com.bacpham.kanban_service.service;

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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
public class ProductService {
    ProductRepository productRepository;
    ProductMapper productMapper;
    CategoryRepository categoryRepository;
    SupplierRepository supplierRepository;
    SubProductRepository subProductRepository;

    public ProductResponse createProduct(ProductCreationRequest request) {
        Product product = productMapper.toProduct(request);

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

    public PageResponse<ProductResponse> getProductPage(int page, int pageSize, String title) {
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

    //get product by id
    public ProductResponse getProductById(String id) {
        return productRepository.findById(id)
                .map(productMapper::toProductResponse)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
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

    public List<ProductResponse> getFilteredProductsNoPaging(
            String size,
            List<String> colors,
            List<String> categoryIds,
            List<Double> priceRange
    ) {
        Double minPrice = (priceRange != null && !priceRange.isEmpty()) ? priceRange.get(0) : null;
        Double maxPrice = (priceRange != null && priceRange.size() > 1) ? priceRange.get(1) : null;

        List<Product> filteredProducts = productRepository.findFilteredProducts(
                categoryIds,
                size,
                colors,
                minPrice,
                maxPrice
        );

        return filteredProducts.stream()
                .map(productMapper::toProductResponse)
                .collect(Collectors.toList());
    }
}