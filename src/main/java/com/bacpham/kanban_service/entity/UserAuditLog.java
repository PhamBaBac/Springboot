package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Bảng UserAuditLog: Ghi lại chi tiết các hành động quản trị tài khoản
 * (Ai tạo tài khoản nào, thời gian, lịch sử thay đổi phân quyền/vai trò).
 */
@Entity
@Table(name = "user_audit_log", indexes = {
        @Index(name = "idx_user_audit_log_created_at", columnList = "created_at"),
        @Index(name = "idx_user_audit_log_target_user", columnList = "target_user_email")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAuditLog extends BaseModel {

    @Column(name = "performed_by_email", length = 100)
    String performedByEmail;

    @Column(name = "performed_by_role", length = 50)
    String performedByRole;

    @Column(name = "action", nullable = false, length = 50)
    String action;

    @Column(name = "target_user_id", length = 100)
    String targetUserId;

    @Column(name = "target_user_email", length = 100)
    String targetUserEmail;

    @Column(name = "details", length = 1000)
    String details;
}
