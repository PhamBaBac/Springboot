package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.UserNotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserNotificationResponse {
    String id;
    String title;
    String content;
    UserNotificationType type;
    String targetUrl;
    String referenceId;
    Boolean isRead;
    Date readAt;
    Date createdAt;
}
