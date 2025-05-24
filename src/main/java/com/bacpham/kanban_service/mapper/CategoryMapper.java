package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.CategoryRequest;
import com.bacpham.kanban_service.dto.response.CategoryResponse;
import com.bacpham.kanban_service.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;
import java.util.Set;
@Mapper(componentModel = "spring")
public interface CategoryMapper {
    Category toCategory(CategoryRequest request);

    CategoryResponse toCategoryResponse(Category category);
    @Mapping(target = "id", ignore = true)
    void updateCategoryFromRequest(CategoryRequest request, @MappingTarget Category category);
}

