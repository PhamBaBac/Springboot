package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.SubProductCreationRequest;
import com.bacpham.kanban_service.dto.response.SubProductResponse;
import com.bacpham.kanban_service.service.ISubProductService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subProducts")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class SubProductController {
    ISubProductService subProductService;

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    ApiResponse<SubProductResponse> createProduct(@RequestBody @Validated SubProductCreationRequest request) {
        return ApiResponse.<SubProductResponse>builder()
                .data(subProductService.createSubProduct(request))
                .build();
    }

    @GetMapping("/get-filter-values")
    ApiResponse<Map<String, List<?>>> getFilterValues(
            @RequestParam(value = "catIds", required = false) List<String> catIds,
            @RequestParam(value = "search", required = false) String search
    ) {
        return ApiResponse.<Map<String, List<?>>>builder()
                .data(subProductService.getSubProducts(catIds, search))
                .build();
    }

    @DeleteMapping("/remove-sub-product/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    ApiResponse<Void> deleteProduct(@PathVariable String id) {
        subProductService.delete(id);
        return ApiResponse.<Void>builder().build();
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    ApiResponse<SubProductResponse> updateProduct(@RequestBody @Validated SubProductCreationRequest request) {
        return ApiResponse.<SubProductResponse>builder()
                .data(subProductService.updateSubProduct(request))
                .build();
    }
    @GetMapping("/get-all-sub-product/{id}")
    ApiResponse<List<SubProductResponse>> getAllSubProduct(@PathVariable String id) {
        return ApiResponse.<List<SubProductResponse>>builder()
                .data(subProductService.getAllSubProduct(id))
                .build();
    }

    @GetMapping("/detail/{id}")
    ApiResponse<Map<String, Object>> getSubProductDetail(@PathVariable String id) {
        com.bacpham.kanban_service.entity.SubProduct sub = subProductService.findById(id);
        com.bacpham.kanban_service.entity.Product product = sub.getProduct();
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("subProductId", sub.getId());
        result.put("productId", product != null ? product.getId() : null);
        result.put("productSlug", product != null ? product.getSlug() : null);
        result.put("productTitle", product != null ? product.getTitle() : null);
        return ApiResponse.<Map<String, Object>>builder()
                .data(result)
                .build();
    }
}
