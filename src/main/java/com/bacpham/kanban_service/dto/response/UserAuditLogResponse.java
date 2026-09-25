package com.bacpham.kanban_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAuditLogResponse {
    private String id;
    private String performedByEmail;
    private String performedByRole;
    private String action;
    private String targetUserId;
    private String targetUserEmail;
    private String details;
    private String createdAt;
}
