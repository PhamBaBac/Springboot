package com.bacpham.kanban_service.dto.request;

import lombok.Data;

@Data
public class ApplyPromotionRequest {
    private String userId;
    private String code;
}