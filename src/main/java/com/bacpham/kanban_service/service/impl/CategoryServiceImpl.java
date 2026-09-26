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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        redisService.delete("categories");

        return response;
    }

    public List<CategoryResponse> getCategories() {
        String key = "categories";
        List<Category> categoriesFromDb = categoryRepository.findAllByDeletedFalse();
        List<CategoryResponse> responses = categoriesFromDb.stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();

        redisService.delete(key);
        if (!responses.isEmpty()) {
            Map<String, CategoryResponse> toCache = responses.stream()
                    .collect(Collectors.toMap(
                            c -> c.getId(),
                            c -> c
                    ));
            redisService.hashSetAll(key, toCache);
            redisService.setTimeToLive(key, 1, TimeUnit.HOURS);
        }
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

    @Override
    public List<CategoryResponse> getCategoryBranch(String categoryId) {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Optional<Category> optCategory = categoryRepository.findById(categoryId.trim());
        if (optCategory.isEmpty() || Boolean.TRUE.equals(optCategory.get().getDeleted())) {
            optCategory = categoryRepository.findAllByDeletedFalse().stream()
                    .filter(c -> categoryId.trim().equalsIgnoreCase(c.getSlug()) || categoryId.trim().equalsIgnoreCase(c.getTitle()))
                    .findFirst();
            if (optCategory.isEmpty()) {
                return Collections.emptyList();
            }
        }

        Category current = optCategory.get();
        Set<String> visited = new HashSet<>();
        visited.add(current.getId());

        while (current.getParentId() != null && !current.getParentId().trim().isEmpty()) {
            String parentId = current.getParentId().trim();
            if (visited.contains(parentId)) {
                break;
            }
            visited.add(parentId);
            Optional<Category> parentOpt = categoryRepository.findById(parentId);
            if (parentOpt.isPresent() && !Boolean.TRUE.equals(parentOpt.get().getDeleted())) {
                current = parentOpt.get();
            } else {
                break;
            }
        }
        Category rootCategory = current;

        List<Category> branch = new ArrayList<>();
        branch.add(rootCategory);
        Set<String> visitedChildren = new HashSet<>();
        visitedChildren.add(rootCategory.getId());
        collectChildren(rootCategory.getId(), branch, visitedChildren);

        return branch.stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();
    }

    private void collectChildren(String parentId, List<Category> result, Set<String> visited) {
        List<Category> children = categoryRepository.findByParentIdAndDeletedFalse(parentId);
        if (children != null && !children.isEmpty()) {
            for (Category child : children) {
                if (child != null && child.getId() != null && !visited.contains(child.getId())) {
                    visited.add(child.getId());
                    result.add(child);
                    collectChildren(child.getId(), result, visited);
                }
            }
        }
    }

    @Transactional
    public void deleteCategory(String categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        category.setDeleted(true);
        categoryRepository.save(category);
        
        redisService.delete("categories");
    }

    public CategoryResponse updateCategory(String categoryId, CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        categoryMapper.updateCategoryFromRequest(request, category);
        category = categoryRepository.save(category);
        
        CategoryResponse response = categoryMapper.toCategoryResponse(category);
        
        redisService.delete("categories");
        
        return response;
    }
}
