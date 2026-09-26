package com.bacpham.kanban_service.configuration.socket;

import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocketChatMessage {
    private String conversationId;
    private String senderId;
    private String receiverId;
    private String content;
    private Role role;
    private String avatar;
    private String username;
    private MessageStatus status;
}
