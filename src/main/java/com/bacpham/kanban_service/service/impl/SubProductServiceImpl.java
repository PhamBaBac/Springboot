package com.bacpham.kanban_service.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bacpham.kanban_service.dto.request.SubProductCreationRequest;
import com.bacpham.kanban_service.dto.response.SubProductResponse;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.SubProductMapper;
import com.bacpham.kanban_service.repository.ProductRepository;
import com.bacpham.kanban_service.repository.SubProductRepository;
import com.bacpham.kanban_service.service.ISubProductService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SubProductServiceImpl implements ISubProductService {
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
        subProduct.setStock(request.getQty());
        syncAttributes(subProduct, request);

        subProduct = subProductRepository.save(subProduct);

        return subProductMapper.toSubProductResponse(subProduct);

    }

    @Override
    public Map<String, List<?>> getSubProducts() {
        return getSubProducts(null, null);
    }

    @Override
    public Map<String, List<?>> getSubProducts(List<String> catIds, String search) {
        if (catIds != null && catIds.isEmpty()) {
            catIds = null;
        }
        if (search != null && search.trim().isEmpty()) {
            search = null;
        }

        Map<String, List<?>> result = new HashMap<>();
        if (catIds != null) {
            result.put("sizes", subProductRepository.findDistinctSizesByCatIds(catIds, search));
            result.put("colors", subProductRepository.findDistinctColorsByCatIds(catIds, search));
            result.put("prices", subProductRepository.findDistinctPricesByCatIds(catIds, search));
        } else {
            result.put("sizes", subProductRepository.findDistinctSizesWithoutCatIds(search));
            result.put("colors", subProductRepository.findDistinctColorsWithoutCatIds(search));
            result.put("prices", subProductRepository.findDistinctPricesWithoutCatIds(search));
        }
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
        syncAttributes(subProduct, request);
        subProduct = subProductRepository.save(subProduct);

        return subProductMapper.toSubProductResponse(subProduct);
    }

    private void syncAttributes(SubProduct subProduct, SubProductCreationRequest request) {
        Map<String, String> attrs = request.getAttributes();
        if (attrs != null && !attrs.isEmpty()) {
            subProduct.setAttributes(attrs);
            // Sync to legacy color if empty
            if (subProduct.getColor() == null || subProduct.getColor().isEmpty()) {
                for (Map.Entry<String, String> entry : attrs.entrySet()) {
                    String k = entry.getKey().toLowerCase();
                    if (k.contains("màu") || k.contains("color")) {
                        subProduct.setColor(entry.getValue());
                        break;
                    }
                }
            }
            // Sync to legacy size if empty
            if (subProduct.getSize() == null || subProduct.getSize().isEmpty()) {
                for (Map.Entry<String, String> entry : attrs.entrySet()) {
                    String k = entry.getKey().toLowerCase();
                    if (k.contains("size") || k.contains("kích") || k.contains("dung lượng") || k.contains("bộ nhớ") || k.contains("storage")) {
                        subProduct.setSize(entry.getValue());
                        break;
                    }
                }
            }
        }
    }

    public List<SubProductResponse> getAllSubProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        List<SubProduct> subProducts = subProductRepository.findAllByProductAndDeletedFalse(product);

        return subProducts.stream()
                .map(subProductMapper::toSubProductResponse)
                .collect(Collectors.toList());
    }
    public SubProduct findById(String subProductId) {
        return subProductRepository.findById(subProductId)
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));
    }
}
