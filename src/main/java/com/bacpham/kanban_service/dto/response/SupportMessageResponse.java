package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.MessageType;
import com.bacpham.kanban_service.enums.Role;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class SupportMessageResponse {
    String conversationId;
    String senderId;
    String receiverId;
    String content;
    MessageType type;
    List<String> images;
    Role role;
    String avatar;
    String username;
    MessageStatus status;
    Date createdAt;
}