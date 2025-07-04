package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.SupplierRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.SupplierResponse;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Supplier;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.SupplierMapper;
import com.bacpham.kanban_service.repository.CategoryRepository;
import com.bacpham.kanban_service.repository.SupplierRepository;
import com.bacpham.kanban_service.service.ISupplierService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SupplierServiceImpl implements ISupplierService {

    SupplierRepository supplierRepository;
    CategoryRepository categoryRepository;
    SupplierMapper supplierMapper;

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        log.info("Creating supplier with request: {}", request.toString());
        Supplier supplier = supplierMapper.toSupplier(request);

        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            List<Category> categories = new ArrayList<>();

            for (String categoryId : request.getCategories()) {
                Optional<Category> optionalCategory = categoryRepository.findById(categoryId);
                if (optionalCategory.isPresent()) {
                    Category category = optionalCategory.get();
                    category.setSupplier(supplier);
                    categories.add(category);
                }
            }
            supplier.setCategories(categories);
        }

        supplier = supplierRepository.save(supplier);

        if (supplier.getCategories() != null && !supplier.getCategories().isEmpty()) {
            categoryRepository.saveAll(supplier.getCategories());
        }

        return supplierMapper.toSupplierResponse(supplier);
    }


    public PageResponse<SupplierResponse> getSupplierResponsePage(int page, int pageSize) {
        Sort sort = Sort.by("createdAt").descending();

        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        Page<Supplier> pageData;

        pageData = supplierRepository.findAllByDeletedFalse(pageable);

        return PageResponse.<SupplierResponse>builder()
                .currentPage(page)
                .pageSize(pageData.getSize())
                .totalPages(pageData.getTotalPages())
                .totalElements(pageData.getTotalElements())
                .data(pageData.getContent().stream().map(supplierMapper::toSupplierResponse).toList())
                .build();
    }

    public void deleteSupplier(String id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));

        supplier.setDeleted(true);
        supplierRepository.save(supplier);
    }

    public SupplierResponse updateSupplier(String id, SupplierRequest request) {
        log.info("all request: {}", request.toString());
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));

        supplierMapper.updateSupplierFromRequest(supplier, request);

        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            List<Category> categories = new ArrayList<>();

            for (String categoryId : request.getCategories()) {
                Optional<Category> optionalCategory = categoryRepository.findById(categoryId);
                if (optionalCategory.isPresent()) {
                    Category category = optionalCategory.get();
                    category.setSupplier(supplier);
                    categories.add(category);
                }
            }
            supplier.setCategories(categories);
        }

        supplier = supplierRepository.save(supplier);

        return supplierMapper.toSupplierResponse(supplier);
    }

    //findAllSupplier
    public List<SupplierResponse> findAllSupplier() {
        List<Supplier> suppliers = supplierRepository.findAll();
        return suppliers.stream()
                .map(supplierMapper::toSupplierResponse)
                .toList();
    }
    public List<SupplierResponse> findSuppliersByDateRange(Date start, Date end) {
        List<Supplier> suppliers = supplierRepository.findByCreatedAtBetweenOptional(start, end);
        return suppliers.stream()
                .map(supplierMapper::toSupplierResponse)
                .collect(Collectors.toList());
    }
    public SupplierResponse getSupplierById(String id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));
        return supplierMapper.toSupplierResponse(supplier);
    }


}
