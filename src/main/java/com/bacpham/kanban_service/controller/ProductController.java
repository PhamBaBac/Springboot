package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ProductCreationRequest;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.service.IProductService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class ProductController {
    IProductService productService;

    @PostMapping
    ApiResponse<ProductResponse> createProduct(@RequestBody @Validated ProductCreationRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .data(productService.createProduct(request))
                .build();
    }

    @DeleteMapping("/{id}")
    ApiResponse<Void> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ApiResponse.<Void>builder().build();
    }

    @PutMapping("/{slug}/{id}")
    ApiResponse<ProductResponse> updateProduct(@PathVariable String id, @RequestBody ProductCreationRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .data(productService.updateProduct(id, request))
                .build();
    }
}
