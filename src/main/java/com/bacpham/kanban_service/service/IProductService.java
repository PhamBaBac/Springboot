package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.ProductCreationRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.ProductResponse;

import java.util.List;

public interface IProductService {

    ProductResponse createProduct(ProductCreationRequest request);

    List<ProductResponse> getProducts();

    PageResponse<ProductResponse> getProductPage(int page, int pageSize, String title);

    void deleteProduct(String id);

    ProductResponse getProductById(String slug, String id);

    ProductResponse updateProduct(String id, ProductCreationRequest request);

    List<ProductResponse> getFilteredProductsNoPaging(
            String size,
            List<String> colors,
            List<String> categoryIds,
            List<Double> priceRange
    );
}
