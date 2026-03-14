package com.bacpham.kanban_service.model;

import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "support_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class SupportMessage extends BaseModel {
    private String conversationId;
    private String senderId;    // ai gửi
    private String receiverId;  // gửi cho ai (có thể null nếu admin offline)
    private String username;    // tên người gửi (user hoặc admin)
    private String avatar;      // avatar người gửi

    @Enumerated(EnumType.STRING)
    private Role role;          // USER / ADMIN / MANAGER

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    @PrePersist
    public void prePersist() {
        if (status == null) status = MessageStatus.PENDING;
    }
}
