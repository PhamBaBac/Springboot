package com.bacpham.kanban_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AiProductSearchResponse {
    private String message; // AI trả lời
    private LocalDateTime aiCreatedAt;
    private List<ProductAiResponse> result;
}
