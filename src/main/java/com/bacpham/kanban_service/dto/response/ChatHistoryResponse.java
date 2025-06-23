package com.bacpham.kanban_service.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistoryResponse {
    private String userMessage;
    private String aiResponse;
    private LocalDateTime userCreatedAt;
    private LocalDateTime aiCreatedAt;
    private String userId;
}
