package com.bacpham.kanban_service.mapper;

import com.bacpham.kanban_service.dto.request.PromotionRequest;
import com.bacpham.kanban_service.dto.response.PromotionResponse;
import com.bacpham.kanban_service.entity.Promotion;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PromotionMapper {
    Promotion toEntity(PromotionRequest request);

    PromotionResponse toResponse(Promotion promotion);

//    void updatePromotionFromRequest(PromotionRequest request, Promotion promotion);
}
