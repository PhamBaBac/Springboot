package com.bacpham.kanban_service.dto.request;

import com.bacpham.kanban_service.enums.NotificationPriority;
import com.bacpham.kanban_service.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record AdminNotificationRequest(
        @NotBlank(message = "Tiêu đề thông báo không được để trống")
        String title,

        @NotBlank(message = "Nội dung thông báo không được để trống")
        String content,

        @NotNull(message = "Loại thông báo không được để trống")
        NotificationType type,

        NotificationPriority priority,
        String targetUrl,
        String referenceId,
        String recipientRole
) {}
