package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.CategoryRequest;
import com.bacpham.kanban_service.dto.response.CategoryResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;

import java.util.List;

public interface ICategoryService {

    CategoryResponse createCategory(CategoryRequest request);

    List<CategoryResponse> getCategories();

    PageResponse<CategoryResponse> getPageCategories(int page, int pageSize);

    void deleteCategory(String categoryId);

    CategoryResponse updateCategory(String categoryId, CategoryRequest request);
}
