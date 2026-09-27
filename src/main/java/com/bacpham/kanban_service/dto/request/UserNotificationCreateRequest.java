package com.bacpham.kanban_service.dto.request;

import com.bacpham.kanban_service.enums.UserNotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserNotificationCreateRequest {
    String userId;
    String title;
    String content;
    UserNotificationType type;
    String targetUrl;
    String referenceId;
}
