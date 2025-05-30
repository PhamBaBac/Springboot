package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.SubProductCreationRequest;
import com.bacpham.kanban_service.dto.response.FilterSubProductResponse;
import com.bacpham.kanban_service.dto.response.SubProductResponse;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.SubProductMapper;
import com.bacpham.kanban_service.repository.ProductRepository;
import com.bacpham.kanban_service.repository.SubProductRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SubProductService {
    SubProductRepository subProductRepository;
    ProductRepository productRepository;
    SubProductMapper subProductMapper;

    public SubProductResponse createSubProduct(SubProductCreationRequest request) {
        SubProduct subProduct = subProductMapper.toSubProduct(request);

        if (request.getProductId() != null && !request.getProductId().isEmpty()) {
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
            subProduct.setProduct(product);
        }

        subProduct = subProductRepository.save(subProduct);

        return subProductMapper.toSubProductResponse(subProduct);

    }

    public Map<String, List<?>> getSubProducts() {
        List<FilterSubProductResponse> subProducts = subProductRepository.findAll().stream()
                .map(subProductMapper::toFilterSubProductResponse)
                .toList();

        List<String> sizes = subProducts.stream()
                .map(FilterSubProductResponse::getSize)
                .map(String::toUpperCase) // Chuyển về chữ thường
                .distinct()
                .collect(Collectors.toList());

        List<String> colors = subProducts.stream()
                .map(FilterSubProductResponse::getColor)
                .map(String::toUpperCase) // Chuyển về chữ thường
                .distinct()
                .collect(Collectors.toList());

        List<Double> prices = subProducts.stream()
                .map(FilterSubProductResponse::getPrice)
                .distinct()
                .collect(Collectors.toList());

        Map<String, List<?>> result = new HashMap<>();
        result.put("sizes", sizes);
        result.put("colors", colors);
        result.put("prices", prices);

        return result;
    }

    public void delete(String id) {
        SubProduct subProduct = subProductRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

        subProduct.setDeleted(true);
        subProductRepository.save(subProduct);
    }

    public SubProductResponse updateSubProduct(SubProductCreationRequest request) {
        SubProduct subProduct = subProductRepository.findById(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

        subProductMapper.updateSubProduct(subProduct, request);
        subProduct = subProductRepository.save(subProduct);

        return subProductMapper.toSubProductResponse(subProduct);
    }

    public List<SubProductResponse> getAllSubProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        List<SubProduct> subProducts = subProductRepository.findAllByProductAndDeletedFalse(product);

        return subProducts.stream()
                .map(subProductMapper::toSubProductResponse)
                .collect(Collectors.toList());
    }
}
