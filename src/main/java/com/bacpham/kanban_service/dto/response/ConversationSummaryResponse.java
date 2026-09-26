package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.Role;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class ConversationSummaryResponse {
    String conversationId;
    String customerId;
    String customerName;
    String customerAvatar;
    String lastMessage;
    Date lastMessageTime;
    Role lastSenderRole;
    long unreadCount;
}
