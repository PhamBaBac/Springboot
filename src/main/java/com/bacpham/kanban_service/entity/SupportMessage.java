package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.MessageType;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

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

    @Enumerated(EnumType.STRING)
    private MessageType type;

    @Column(columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> images;

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    @PrePersist
    public void prePersist() {
        if (status == null) status = MessageStatus.PENDING;
        if (type == null) {
            type = (images != null && !images.isEmpty()) ? MessageType.IMAGE : MessageType.TEXT;
        }
    }
}
