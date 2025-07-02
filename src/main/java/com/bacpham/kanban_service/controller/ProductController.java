package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ProductCreationRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.service.impl.ProductServiceImpl;
import com.bacpham.kanban_service.service.UserActivityService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class ProductController {
    ProductServiceImpl productService;
    private final JobLauncher jobLauncher;
    private final Job productJob;

    @PostMapping
    ApiResponse<ProductResponse> createProduct(@RequestBody @Validated ProductCreationRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.createProduct(request))
                .build();
    }

    @GetMapping
    ApiResponse<List<ProductResponse>> getProducts() {
        return ApiResponse.<List<ProductResponse>>builder()
                .result(productService.getProducts())
                .build();
    }

    @GetMapping("/page")
    ApiResponse<PageResponse<ProductResponse>> productPage(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize,
            @RequestParam(value = "title", required = false) String title
    ) {
        return ApiResponse.<PageResponse<ProductResponse>>builder()
                .result(productService.getProductPage(page, pageSize, title))
                .build();
    }

    @DeleteMapping("/{id}")
    ApiResponse<Void> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/{slug}/{id}")
    ApiResponse<ProductResponse> getProduct(
            @PathVariable String slug,
            @PathVariable String id
    ) {
        ProductResponse productResponse = productService.getProductById(slug, id);

        return ApiResponse.<ProductResponse>builder()
                .result(productResponse)
                .build();
    }

    @PutMapping("/{slug}/{id}")
    ApiResponse<ProductResponse> updateProduct(@PathVariable String id, @RequestBody ProductCreationRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.updateProduct(id, request))
                .build();
    }
    @GetMapping("/filter")
    public ApiResponse<PageResponse<ProductResponse>> filterProducts(
            @RequestParam(value = "catIds", required = false) List<String> catIds,
            @RequestParam(value = "sizes", required = false) List<String> sizes,
            @RequestParam(value = "colors", required = false) List<String> colors,
            @RequestParam(value = "price", required = false) List<Double> price,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "12") int pageSize
    ) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);

        Page<ProductResponse> result = productService.getFilteredProducts(
                catIds,
                sizes,
                colors,
                price,
                pageable
        );

        PageResponse<ProductResponse> response = PageResponse.<ProductResponse>builder()
                .currentPage(result.getNumber() + 1)
                .totalPages(result.getTotalPages())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .data(result.getContent())
                .build();

        return ApiResponse.<PageResponse<ProductResponse>>builder()
                .result(response)
                .build();
    }

    @PostMapping("/batch/products")
    public void runProductImportJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("startAt", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(productJob, jobParameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PostMapping("/listProductRecommendations")
    public ApiResponse<List<ProductResponse>> getProductsByIds(@RequestBody List<String> ids) {
        log.info("Received request to get product recommendations for IDs: {}", ids);
        List<ProductResponse> result = productService.getListProductRecommendations(ids);
        return ApiResponse.<List<ProductResponse>>builder()
                .result(result)
                .message("success")
                .build();
    }
    @GetMapping("/bestSellers")
    public ApiResponse<List<ProductResponse>> getBestSellers() {
        return ApiResponse.<List<ProductResponse>>builder()
                .result(productService.getBestSellers())
                .build();
    }

}
