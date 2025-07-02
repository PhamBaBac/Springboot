package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.CategoryRequest;
import com.bacpham.kanban_service.dto.response.CategoryResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.CategoryMapper;
import com.bacpham.kanban_service.repository.CategoryRepository;
import com.bacpham.kanban_service.service.ICategoryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CategoryServiceImpl implements ICategoryService {

    CategoryRepository categoryRepository;
    CategoryMapper categoryMapper;
    GenericRedisService<String, String, CategoryResponse> redisService;

    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = categoryMapper.toCategory(request);
        category = categoryRepository.save(category);

        CategoryResponse response = categoryMapper.toCategoryResponse(category);
        redisService.hashSet("categories", category.getId(), response); // lưu theo id

        return response;
    }

    public List<CategoryResponse> getCategories() {
        String key = "categories";
        Map<String, CategoryResponse> cached = redisService.getField(key);
        if (cached != null && !cached.isEmpty()) {
            return new ArrayList<>(cached.values());
        }

        List<Category> categoriesFromDb = categoryRepository.findAll();
        List<CategoryResponse> responses = categoriesFromDb.stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();

        Map<String, CategoryResponse> toCache = responses.stream()
                .collect(Collectors.toMap(
                        c -> c.getId(), // field
                        c -> c
                ));

        redisService.hashSetAll(key, toCache);
        redisService.setTimeToLive(key, 1, TimeUnit.HOURS);
        return responses;
    }

    public PageResponse<CategoryResponse> getPageCategories(int page, int pageSize) {
        Sort sort = Sort.by("createdAt").descending();
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        Page<Category> pageData;
        pageData = categoryRepository.findAllByDeletedFalse(pageable);

        return PageResponse.<CategoryResponse>builder()
                .currentPage(page)
                .pageSize(pageData.getSize())
                .totalPages(pageData.getTotalPages())
                .totalElements(pageData.getTotalElements())
                .data(pageData.getContent().stream().map(categoryMapper::toCategoryResponse).toList())
                .build();
    }

    @Transactional
    public void deleteCategory(String categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        category.setDeleted(true);
        categoryRepository.save(category);
        
        // Remove from cache
        redisService.delete("categories", categoryId);
    }

    public CategoryResponse updateCategory(String categoryId, CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        categoryMapper.updateCategoryFromRequest(request, category);
        category = categoryRepository.save(category);
        
        CategoryResponse response = categoryMapper.toCategoryResponse(category);
        
        // Update cache
        redisService.hashSet("categories", categoryId, response);
        
        return response;
    }
}
