package com.bacpham.kanban_service.dto.response;

import lombok.*;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistoryResponse {
    private String role;
    private String message;
    private Date createdAt;
    private List<ProductResponse> products;
}
