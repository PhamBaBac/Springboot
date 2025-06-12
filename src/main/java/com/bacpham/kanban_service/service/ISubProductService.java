package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.SubProductCreationRequest;
import com.bacpham.kanban_service.dto.response.SubProductResponse;

import java.util.List;
import java.util.Map;

public interface ISubProductService {
    SubProductResponse createSubProduct(SubProductCreationRequest request);
    Map<String, List<?>> getSubProducts();
    void delete(String id);
    SubProductResponse updateSubProduct(SubProductCreationRequest request);
    List<SubProductResponse> getAllSubProduct(String id);
}
