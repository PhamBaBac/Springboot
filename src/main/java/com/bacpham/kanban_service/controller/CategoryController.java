package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.CategoryRequest;
import com.bacpham.kanban_service.dto.response.CategoryResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class CategoryController {
    CategoryService categoryService;
    private final JobLauncher jobLauncher;
    private final Job categoryJob;
    @PostMapping
    public ApiResponse<CategoryResponse> createCategory(@RequestBody @Validated CategoryRequest request) {

        return ApiResponse.<CategoryResponse>builder()
                .result(categoryService.createCategory(request))
                .build();
    }

    @GetMapping("/all")
    ApiResponse<List<CategoryResponse>> getCategory() {
        return ApiResponse.<List<CategoryResponse>>builder()
                .result(categoryService.getCategories())
                .build();
    }

    @DeleteMapping("/{categoryId}")
    ApiResponse<Void> deleteCategory(@PathVariable String categoryId) {
        categoryService.deleteCategory(categoryId);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/page")
    ApiResponse<PageResponse<CategoryResponse>> categoryPage(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize
    ) {
        return ApiResponse.<PageResponse<CategoryResponse>>builder()
                .result(categoryService.getPageCategories(page, pageSize))
                .build();
    }

    @PutMapping("/{categoryId}")
    ApiResponse<CategoryResponse> updateCategory(
            @PathVariable String categoryId,
            @RequestBody @Validated CategoryRequest request) {
        return ApiResponse.<CategoryResponse>builder()
                .result(categoryService.updateCategory(categoryId, request))
                .build();
    }



    @PostMapping("/batch/categories")
    public void runCategoryImportJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("startAt", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(categoryJob, jobParameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
