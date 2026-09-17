package com.bacpham.kanban_service.dto.response;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistoryResponse {
    private String role;
    private String message;
    private Date createdAt;
}
