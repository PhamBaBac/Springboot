package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.NotificationPriority;
import com.bacpham.kanban_service.enums.NotificationType;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "admin_notifications", indexes = {
        @Index(name = "idx_admin_noti_read_created", columnList = "is_read, created_at"),
        @Index(name = "idx_admin_noti_type", columnList = "type"),
        @Index(name = "idx_admin_noti_deleted_created", columnList = "deleted, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminNotification extends BaseModel {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", length = 1000, nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "target_url")
    private String targetUrl;

    @Column(name = "reference_id")
    private String referenceId;

    @Builder.Default
    @Column(name = "is_read", columnDefinition = "boolean default false")
    private Boolean isRead = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "read_at")
    private Date readAt;

    @Column(name = "recipient_role")
    @Builder.Default
    private String recipientRole = "ADMIN";
}
