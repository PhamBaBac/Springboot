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
    boolean isPromotionValid (String code);

    boolean applyPromotionCode(String userId, String code);

}
