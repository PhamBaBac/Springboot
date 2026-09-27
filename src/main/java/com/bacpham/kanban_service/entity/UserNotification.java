package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.UserNotificationType;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "user_notifications", indexes = {
        @Index(name = "idx_user_noti_user_read", columnList = "user_id, is_read, created_at"),
        @Index(name = "idx_user_noti_user_type", columnList = "user_id, type"),
        @Index(name = "idx_user_noti_user_deleted", columnList = "user_id, deleted, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotification extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", length = 1000, nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private UserNotificationType type;

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
}
