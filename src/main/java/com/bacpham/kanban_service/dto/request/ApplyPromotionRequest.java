package com.bacpham.kanban_service.dto.request;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class ApplyPromotionRequest {
    private String userId;
    private String code;
}