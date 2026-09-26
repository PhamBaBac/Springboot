package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.NotificationPriority;
import com.bacpham.kanban_service.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class AdminNotificationResponse {
    String id;
    String title;
    String content;
    NotificationType type;
    NotificationPriority priority;
    String targetUrl;
    String referenceId;
    Boolean isRead;
    Date readAt;
    String recipientRole;
    Date createdAt;
    Date updatedAt;
}
