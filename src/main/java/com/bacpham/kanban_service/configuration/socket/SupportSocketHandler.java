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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
@RequiredArgsConstructor
public class SupportSocketHandler {

    private final SocketIOServer socketIOServer;
    private final ISupportMessageService supportMessageService;
    private final SupportMessageMapper supportMessageMapper;
    private final ApplicationEventPublisher eventPublisher;

    public static final String ADMIN_CHANNEL = "admin_support_channel";

    private final Map<String, List<Long>> rateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_MESSAGES_PER_WINDOW = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 3000;

    private final Map<String, Long> lastNotificationTimeMap = new ConcurrentHashMap<>();
    private static final long NOTIFICATION_COOLDOWN_MS = 2 * 60 * 1000;

    @PostConstruct
    public void registerListeners() {
        socketIOServer.addConnectListener(client -> {
            log.info("Socket client connected: sessionId={}", client.getSessionId());
        });

        socketIOServer.addDisconnectListener(client -> {
            log.info("Socket client disconnected: sessionId={}", client.getSessionId());
        });

        socketIOServer.addEventListener("join_admin_channel", Map.class, (client, data, ackSender) -> {
            client.joinRoom(ADMIN_CHANNEL);
            log.info("Staff client {} joined room {}", client.getSessionId(), ADMIN_CHANNEL);
        });
        socketIOServer.addEventListener("join_conversation", Map.class, (client, data, ackSender) -> {
            String conversationId = data != null ? (String) data.get("conversationId") : null;
            if (conversationId != null && !conversationId.isBlank()) {
                String room = "conversation_" + conversationId;
                client.joinRoom(room);
                log.info("Client {} joined room {}", client.getSessionId(), room);
            }
        });

        socketIOServer.addEventListener("leave_conversation", Map.class, (client, data, ackSender) -> {
            String conversationId = data != null ? (String) data.get("conversationId") : null;
            if (conversationId != null && !conversationId.isBlank()) {
                String room = "conversation_" + conversationId;
                client.leaveRoom(room);
                log.info("Client {} left room {}", client.getSessionId(), room);
            }
        });

        socketIOServer.addEventListener("send_message", SocketChatMessage.class, (client, messageData, ackSender) -> {
            if (messageData == null || messageData.getContent() == null || messageData.getContent().trim().isEmpty()) {
                return;
            }

            Role role = messageData.getRole() != null ? messageData.getRole() : Role.USER;

            String senderId = messageData.getSenderId();
            if (role == Role.USER && senderId != null && !senderId.isBlank()) {
                long now = System.currentTimeMillis();
                List<Long> timestamps = rateLimitMap.computeIfAbsent(senderId, k -> new CopyOnWriteArrayList<>());
                timestamps.removeIf(t -> now - t > RATE_LIMIT_WINDOW_MS);
                if (timestamps.size() >= MAX_MESSAGES_PER_WINDOW) {
                    log.warn("User {} gửi tin nhắn quá nhanh, chặn spam", senderId);
                    client.sendEvent("spam_warning", Map.of(
                            "message", "Bạn đang gửi tin nhắn quá nhanh. Vui lòng chậm lại một chút nhé!"
                    ));
                    return;
                }
                timestamps.add(now);
            }

            log.info("Received socket message: from={}, role={}, conv={}",
                    messageData.getUsername(), role, messageData.getConversationId());

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

            String convRoom = "conversation_" + response.getConversationId();
            socketIOServer.getRoomOperations(convRoom).sendEvent("receive_message", response);

            socketIOServer.getRoomOperations(ADMIN_CHANNEL).sendEvent("admin_channel_message", response);

            if (role == Role.USER) {
                String convId = messageData.getConversationId();
                long now = System.currentTimeMillis();
                Long lastNotiTime = (convId != null) ? lastNotificationTimeMap.get(convId) : null;

                if (lastNotiTime == null || (now - lastNotiTime) > NOTIFICATION_COOLDOWN_MS) {
                    if (convId != null) {
                        lastNotificationTimeMap.put(convId, now);
                    }

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
                            convId
                    ));
                } else {
                    log.info("Bỏ qua tạo thông báo hệ thống cho conversation {} do đang trong Cooldown chống spam", convId);
                }
            }
        });

        socketIOServer.addEventListener("typing", Map.class, (client, data, ackSender) -> {
            String conversationId = data != null ? (String) data.get("conversationId") : null;
            if (conversationId != null && !conversationId.isBlank()) {
                String room = "conversation_" + conversationId;
                socketIOServer.getRoomOperations(room).sendEvent("user_typing", data);
            }
        });
    }
}
