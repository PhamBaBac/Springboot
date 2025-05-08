package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.SubProductCreationRequest;
import com.bacpham.kanban_service.dto.response.SubProductResponse;
import com.bacpham.kanban_service.entity.Category;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.exception.AppException;
import com.bacpham.kanban_service.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.SubProductMapper;
import com.bacpham.kanban_service.repository.ProductRepository;
import com.bacpham.kanban_service.repository.SubProductRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        log.info("Creating sub product: {}", request.getImages());
        SubProduct subProduct = subProductMapper.toSubProduct(request);

        if (request.getProductId() != null && !request.getProductId().isEmpty()) {
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
            subProduct.setProduct(product);
        }

        subProduct = subProductRepository.save(subProduct);
        log.info("subProduct", subProduct);

        return subProductMapper.toSubProductResponse(subProduct);

    }

    public List<SubProductResponse> getSubProducts() {
        return subProductRepository.findAll().stream()
                .map(subProductMapper::toSubProductResponse)
                .collect(Collectors.toList());
    }

    public void delete(String id) {
        subProductRepository.deleteById(id);
    }

    public SubProductResponse updateSubProduct(SubProductCreationRequest request) {
        //log ra cac thong tin cua request
        log.info("Updating sub product id: {}", request.getId());
        SubProduct subProduct = subProductRepository.findById(request.getId())
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

        subProductMapper.updateSubProduct(subProduct, request);
        subProduct = subProductRepository.save(subProduct);

        return subProductMapper.toSubProductResponse(subProduct);
    }
}
