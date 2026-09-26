package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

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
    private String senderId;
    private String receiverId;
    private String username;
    private String avatar;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    @PrePersist
    public void prePersist() {
        if (status == null) status = MessageStatus.PENDING;
    }
}
