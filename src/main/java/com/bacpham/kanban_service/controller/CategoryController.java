package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.CategoryRequest;
import com.bacpham.kanban_service.dto.response.CategoryResponse;
import com.bacpham.kanban_service.service.ICategoryService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class CategoryController {
    ICategoryService categoryService;
    JobLauncher jobLauncher;
    Job categoryJob;

    @PostMapping
    @PreAuthorize("hasAuthority('admin:create')")
    public ApiResponse<CategoryResponse> createCategory(@RequestBody @Validated CategoryRequest request) {

        return ApiResponse.<CategoryResponse>builder()
                .data(categoryService.createCategory(request))
                .build();
    }


    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('admin:delete')")
    ApiResponse<Void> deleteCategory(@PathVariable String categoryId) {
        categoryService.deleteCategory(categoryId);
        return ApiResponse.<Void>builder().build();
    }


    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('admin:update')")
    ApiResponse<CategoryResponse> updateCategory(
            @PathVariable String categoryId,
            @RequestBody @Validated CategoryRequest request) {
        return ApiResponse.<CategoryResponse>builder()
                .data(categoryService.updateCategory(categoryId, request))
                .build();
    }



    @PostMapping("/batch/categories")
    @PreAuthorize("hasAuthority('admin:create')")
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
