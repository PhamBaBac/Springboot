package com.bacpham.kanban_service.gemini.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiGenerateRequest {
    private String type;
    private String title;
    private String context;
}
