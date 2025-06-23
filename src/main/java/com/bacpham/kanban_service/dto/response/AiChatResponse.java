package com.bacpham.kanban_service.dto.response;

import lombok.*;
import org.apache.poi.hpsf.Decimal;

import java.time.LocalDateTime;
import java.util.Date;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatResponse {
    private String message;
    private LocalDateTime aiCreatedAt;
}
