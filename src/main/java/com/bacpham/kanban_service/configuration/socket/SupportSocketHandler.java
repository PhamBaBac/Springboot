package com.bacpham.kanban_service.configuration.socket;

import com.bacpham.kanban_service.dto.request.SupportMessageRequest;
import com.bacpham.kanban_service.dto.response.SupportMessageResponse;
import com.bacpham.kanban_service.entity.SupportMessage;
import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.mapper.SupportMessageMapper;
import com.bacpham.kanban_service.service.ISupportMessageService;
import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bacpham.kanban_service.enums.NotificationPriority;
import com.bacpham.kanban_service.enums.NotificationType;
import com.bacpham.kanban_service.event.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class SupportSocketHandler {

    private final SocketIOServer socketIOServer;
    private final ISupportMessageService supportMessageService;
    private final SupportMessageMapper supportMessageMapper;
    private final ApplicationEventPublisher eventPublisher;

    public static final String ADMIN_CHANNEL = "admin_support_channel";

    @PostConstruct
    public void registerListeners() {
        socketIOServer.addConnectListener(client -> {
            log.info("Socket client connected: sessionId={}", client.getSessionId());
        });

        socketIOServer.addDisconnectListener(client -> {
            log.info("Socket client disconnected: sessionId={}", client.getSessionId());
        });

        // Event: Admin/Manager joins the general admin notification channel
        socketIOServer.addEventListener("join_admin_channel", Map.class, (client, data, ackSender) -> {
            client.joinRoom(ADMIN_CHANNEL);
            log.info("Staff client {} joined room {}", client.getSessionId(), ADMIN_CHANNEL);
        });

        // Event: Client joins a specific conversation room
        socketIOServer.addEventListener("join_conversation", Map.class, (client, data, ackSender) -> {
            String conversationId = data != null ? (String) data.get("conversationId") : null;
            if (conversationId != null && !conversationId.isBlank()) {
                String room = "conversation_" + conversationId;
                client.joinRoom(room);
                log.info("Client {} joined room {}", client.getSessionId(), room);
            }
        });

        // Event: Client leaves a conversation room
        socketIOServer.addEventListener("leave_conversation", Map.class, (client, data, ackSender) -> {
            String conversationId = data != null ? (String) data.get("conversationId") : null;
            if (conversationId != null && !conversationId.isBlank()) {
                String room = "conversation_" + conversationId;
                client.leaveRoom(room);
                log.info("Client {} left room {}", client.getSessionId(), room);
            }
        });

        // Event: Send a message
        socketIOServer.addEventListener("send_message", SocketChatMessage.class, (client, messageData, ackSender) -> {
            if (messageData == null || messageData.getContent() == null || messageData.getContent().trim().isEmpty()) {
                return;
            }

            log.info("Received socket message: from={}, role={}, conv={}",
                    messageData.getUsername(), messageData.getRole(), messageData.getConversationId());

            Role role = messageData.getRole() != null ? messageData.getRole() : Role.USER;

            SupportMessageRequest request = SupportMessageRequest.builder()
                    .conversationId(messageData.getConversationId())
                    .senderId(messageData.getSenderId())
                    .receiverId(messageData.getReceiverId())
                    .content(messageData.getContent().trim())
                    .role(role)
                    .avatar(messageData.getAvatar())
                    .username(messageData.getUsername())
                    .status(MessageStatus.SENT)
                    .createdAt(new Date())
                    .build();

            SupportMessage saved = supportMessageService.saveMessage(request);
            SupportMessageResponse response = supportMessageMapper.toSupportMessageResponse(saved);

            // 1. Broadcast to everyone currently in this conversation room
            String convRoom = "conversation_" + response.getConversationId();
            socketIOServer.getRoomOperations(convRoom).sendEvent("receive_message", response);

            // 2. Broadcast to all admins so any connected admin receives updates in real time
            socketIOServer.getRoomOperations(ADMIN_CHANNEL).sendEvent("admin_channel_message", response);

            // 3. Nếu là khách hàng gửi tin nhắn, phát sinh thông báo Realtime cho Admin
            if (role == Role.USER) {
                String senderName = messageData.getUsername() != null && !messageData.getUsername().isBlank()
                        ? messageData.getUsername() : "Khách hàng";
                String snippet = messageData.getContent() != null && messageData.getContent().length() > 60
                        ? messageData.getContent().substring(0, 57) + "..."
                        : messageData.getContent();

                eventPublisher.publishEvent(NotificationEvent.of(
                        this,
                        NotificationType.SUPPORT_MESSAGE,
                        "Tin nhắn hỗ trợ từ " + senderName,
                        snippet,
                        NotificationPriority.NORMAL,
                        "/support",
                        messageData.getConversationId()
                ));
            }
        });

        // Event: Typing indicator
        socketIOServer.addEventListener("typing", Map.class, (client, data, ackSender) -> {
            String conversationId = data != null ? (String) data.get("conversationId") : null;
            if (conversationId != null && !conversationId.isBlank()) {
                String room = "conversation_" + conversationId;
                socketIOServer.getRoomOperations(room).sendEvent("user_typing", data);
            }
        });
    }
}
