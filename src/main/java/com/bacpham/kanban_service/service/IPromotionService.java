package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.PromotionRequest;
import com.bacpham.kanban_service.dto.response.PromotionResponse;

import java.util.List;

public interface IPromotionService {

    PromotionResponse createPromotion(PromotionRequest request);

    PromotionResponse updatePromotion(String id, PromotionRequest request);

    PromotionResponse getPromotionById(String id);

    PromotionResponse getPromotionByNameCode(String code);

    List<PromotionResponse> getAllPromotions();

    void deletePromotion(String id);

    boolean isPromotionValid(String code);

    boolean isPromotionValidForUser(String code, String userId);

    boolean applyPromotionCode(String userId, String code);

    void rollbackPromotionCode(String userId, String code);

    void recordPromotionUsage(com.bacpham.kanban_service.entity.Promotion promotion, String userId, String orderId, Double discountAmount);

    void rollbackPromotionUsage(String orderId);

    String generateUniqueCode(String prefix);
}
