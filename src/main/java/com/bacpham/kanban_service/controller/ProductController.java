package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ProductCreationRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.service.ProductService;
import com.bacpham.kanban_service.service.UserActivityService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
@SecurityRequirement(name = "bearerAuth")
public class ProductController {
    ProductService productService;
    UserActivityService userActivityService;
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

    @GetMapping("/{id}")
    ApiResponse<ProductResponse> getProduct(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser
    ) {
        ProductResponse productResponse = productService.getProductById(id);


        if (currentUser != null) {
            userActivityService.recordViewProductActivity(currentUser, id);
        }

        return ApiResponse.<ProductResponse>builder()
                .result(productResponse)
                .build();
    }

    @PutMapping("/{id}")
    ApiResponse<ProductResponse> updateProduct(@PathVariable String id, @RequestBody ProductCreationRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .result(productService.updateProduct(id, request))
                .build();
    }
    @GetMapping("/filter-products")
    public ApiResponse<List<ProductResponse>> filterProducts(
            @RequestParam(value = "categories", required = false) List<String> categories,
            @RequestParam(value = "size", required = false) String size,
            @RequestParam(value = "colors", required = false) List<String> colors,
            @RequestParam(value = "price", required = false) List<Double> priceRange
    ) {
        return ApiResponse.<List<ProductResponse>>builder()
                .result(productService.getFilteredProductsNoPaging(size, colors, categories, priceRange))
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
}
