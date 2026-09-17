package com.bacpham.kanban_service.dto.response;

import lombok.*;
import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatResponse {
    private String message;
    private LocalDateTime aiCreatedAt;
}
