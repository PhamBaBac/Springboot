package com.bacpham.kanban_service.dto.request;

import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.Role;
import lombok.Builder;

import java.util.Date;

@Builder
public record SupportMessageRequest(
        String conversationId,
        String senderId,
        String receiverId,
        String content,
        Role role,
        String avatar,
        String username,
        MessageStatus status,
        Date createdAt
) {}
