package com.bacpham.kanban_service.dto.request;

import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class ChatRequest {
    private String message;
}