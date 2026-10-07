package com.bacpham.kanban_service.dto.request;

import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.MessageType;
import com.bacpham.kanban_service.enums.Role;
import lombok.Builder;

import java.util.Date;
import java.util.List;

@Builder
public record SupportMessageRequest(
        String conversationId,
        String senderId,
        String receiverId,
        String content,
        MessageType type,
        List<String> images,
        Role role,
        String avatar,
        String username,
        MessageStatus status,
        Date createdAt
) {}
