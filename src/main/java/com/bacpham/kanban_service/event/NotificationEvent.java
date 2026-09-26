package com.bacpham.kanban_service.event;

import com.bacpham.kanban_service.dto.request.AdminNotificationRequest;
import com.bacpham.kanban_service.enums.NotificationPriority;
import com.bacpham.kanban_service.enums.NotificationType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NotificationEvent extends ApplicationEvent {

    private final AdminNotificationRequest request;

    public NotificationEvent(Object source, AdminNotificationRequest request) {
        super(source);
        this.request = request;
    }

    public static NotificationEvent of(
            Object source,
            NotificationType type,
            String title,
            String content,
            NotificationPriority priority,
            String targetUrl,
            String referenceId
    ) {
        AdminNotificationRequest req = AdminNotificationRequest.builder()
                .type(type)
                .title(title)
                .content(content)
                .priority(priority != null ? priority : NotificationPriority.NORMAL)
                .targetUrl(targetUrl)
                .referenceId(referenceId)
                .recipientRole("ADMIN")
                .build();
        return new NotificationEvent(source, req);
    }

    public static NotificationEvent of(
            Object source,
            NotificationType type,
            String title,
            String content,
            NotificationPriority priority,
            String targetUrl,
            String referenceId,
            String recipientRole
    ) {
        AdminNotificationRequest req = AdminNotificationRequest.builder()
                .type(type)
                .title(title)
                .content(content)
                .priority(priority != null ? priority : NotificationPriority.NORMAL)
                .targetUrl(targetUrl)
                .referenceId(referenceId)
                .recipientRole(recipientRole != null ? recipientRole : "ADMIN")
                .build();
        return new NotificationEvent(source, req);
    }
}
