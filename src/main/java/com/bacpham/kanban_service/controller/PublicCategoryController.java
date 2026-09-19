package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.CategoryResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.service.ICategoryService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/categories")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class PublicCategoryController {
    ICategoryService categoryService;

    @GetMapping("/all")
    ApiResponse<List<CategoryResponse>> getCategory() {
        return ApiResponse.<List<CategoryResponse>>builder()
                .data(categoryService.getCategories())
                .build();
    }

    @GetMapping("/page")
    ApiResponse<PageResponse<CategoryResponse>> categoryPage(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize
    ) {
        return ApiResponse.<PageResponse<CategoryResponse>>builder()
                .data(categoryService.getPageCategories(page, pageSize))
                .build();
    }

    @GetMapping("/branch")
    ApiResponse<List<CategoryResponse>> getCategoryBranch(@RequestParam("catId") String catId) {
        return ApiResponse.<List<CategoryResponse>>builder()
                .data(categoryService.getCategoryBranch(catId))
                .build();
    }


}
