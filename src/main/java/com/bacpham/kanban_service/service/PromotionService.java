package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.PromotionRequest;
import com.bacpham.kanban_service.dto.response.PromotionResponse;

import java.util.List;
import java.util.UUID;

public interface PromotionService {

    PromotionResponse createPromotion(PromotionRequest request);

    PromotionResponse updatePromotion(String id, PromotionRequest request);

    PromotionResponse getPromotionById(String id);

    List<PromotionResponse> getAllPromotions();

    void deletePromotion(String id);
}
