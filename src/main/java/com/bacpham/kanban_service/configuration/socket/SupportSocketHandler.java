package com.bacpham.kanban_service.configuration.socket;

import com.bacpham.kanban_service.configuration.security.JwtService;
import com.bacpham.kanban_service.dto.request.SupportMessageRequest;
import com.bacpham.kanban_service.dto.response.SupportMessageResponse;
import com.bacpham.kanban_service.entity.SupportMessage;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.enums.MessageStatus;
import com.bacpham.kanban_service.enums.MessageType;
import com.bacpham.kanban_service.enums.NotificationPriority;
import com.bacpham.kanban_service.enums.NotificationType;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.event.NotificationEvent;
import com.bacpham.kanban_service.mapper.SupportMessageMapper;
import com.bacpham.kanban_service.service.ISupportMessageService;
import com.bacpham.kanban_service.service.UserCacheService;
import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final JwtService jwtService;
    private final UserCacheService userCacheService;

    public static final String ADMIN_CHANNEL = "admin_support_channel";

    private final Map<String, List<Long>> rateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_MESSAGES_PER_WINDOW = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 3000;

    private final Map<String, Long> lastNotificationTimeMap = new ConcurrentHashMap<>();
    private static final long NOTIFICATION_COOLDOWN_MS = 2 * 60 * 1000;

    @PostConstruct
    public void registerListeners() {
        socketIOServer.addConnectListener(client -> {
            String token = SocketAuthorizationListener.extractToken(client.getHandshakeData());
            if (token != null) {
                try {
                    String email = jwtService.extractUsername(token);
                    if (email != null && !email.isBlank()) {
                        User user = userCacheService.getUserByEmail(email);
                        if (user != null) {
                            // Lưu danh tính người dùng vào session của client socket
                            client.set("userId", user.getId());
                            client.set("email", user.getEmail());
                            client.set("role", user.getRole());
                            client.set("username", user.getFirstname() + " " + user.getLastname());
                            client.set("avatar", user.getAvatarUrl());

                            // Tự động gia nhập room cá nhân của chính user
                            String personalRoom = "user_" + user.getId();
                            client.joinRoom(personalRoom);

                            log.info("Socket client connected: sessionId={}, email={}, role={}, auto-joined room {}",
                                    client.getSessionId(), user.getEmail(), user.getRole(), personalRoom);
                            return;
                        }
                    }
                } catch (Exception e) {
                    log.error("Lỗi khởi tạo session socket cho client {}: {}", client.getSessionId(), e.getMessage());
                }
            }
            log.info("Socket client connected: sessionId={}", client.getSessionId());
        });

        socketIOServer.addDisconnectListener(client -> {
            log.info("Socket client disconnected: sessionId={}, userId={}",
                    client.getSessionId(), client.get("userId"));
        });

        // 1. Phân quyền kênh Quản trị viên (Chỉ ADMIN và MANAGER được phép vào)
        socketIOServer.addEventListener("join_admin_channel", Map.class, (client, data, ackSender) -> {
            Role role = client.get("role");
            if (role != Role.ADMIN && role != Role.MANAGER) {
                log.warn("Cảnh báo bảo mật: Client {} (role={}) cố tình vào kênh ADMIN mà không có quyền!",
                        client.getSessionId(), role);
                client.sendEvent("permission_denied", Map.of(
                        "message", "Bạn không có quyền truy cập kênh quản trị viên!"
                ));
                return;
            }
            client.joinRoom(ADMIN_CHANNEL);
            log.info("Nhân viên {} (role={}) đã vào room {}", client.getSessionId(), role, ADMIN_CHANNEL);
        });

        // 2. Kênh thông báo cá nhân người dùng
        socketIOServer.addEventListener("join_user_channel", Map.class, (client, data, ackSender) -> {
            String sessionUserId = client.get("userId");
            Role role = client.get("role");
            String requestedUserId = data != null ? (String) data.get("userId") : null;

            if (sessionUserId == null) {
                log.warn("Client {} chưa xác thực gọi join_user_channel", client.getSessionId());
                return;
            }

            // User thường chỉ được vào room cá nhân của chính mình.
            // Admin/Manager được phép vào room người dùng để hỗ trợ realtime.
            if (requestedUserId == null || requestedUserId.isBlank() || requestedUserId.equals(sessionUserId)) {
                String room = "user_" + sessionUserId;
                client.joinRoom(room);
                log.info("Client {} (role={}) joined personal room {}", client.getSessionId(), role, room);
            } else if (role == Role.ADMIN || role == Role.MANAGER) {
                String room = "user_" + requestedUserId.trim();
                client.joinRoom(room);
                log.info("Staff {} joined user room {}", client.getSessionId(), room);
            } else {
                log.warn("Cảnh báo: User {} cố tình join room của User {} khác -> Bị từ chối!",
                        sessionUserId, requestedUserId);
                client.sendEvent("permission_denied", Map.of(
                        "message", "Bạn không có quyền truy cập kênh của người dùng khác!"
                ));
            }
        });

        socketIOServer.addEventListener("leave_user_channel", Map.class, (client, data, ackSender) -> {
            String sessionUserId = client.get("userId");
            if (sessionUserId != null) {
                String room = "user_" + sessionUserId;
                client.leaveRoom(room);
                log.info("Client {} left user room {}", client.getSessionId(), room);
            }
        });

        // 3. Phân quyền cuộc trò chuyện hỗ trợ
        socketIOServer.addEventListener("join_conversation", Map.class, (client, data, ackSender) -> {
            String conversationId = data != null ? (String) data.get("conversationId") : null;
            if (conversationId == null || conversationId.isBlank()) {
                return;
            }

            String sessionUserId = client.get("userId");
            Role role = client.get("role");

            // Khách hàng thường (USER) chỉ được vào cuộc trò chuyện của chính mình ("user_" + sessionUserId)
            // ADMIN và MANAGER được phép tham gia mọi cuộc trò chuyện để tư vấn
            if (role == Role.USER && sessionUserId != null) {
                String allowedRoom = "user_" + sessionUserId;
                if (!conversationId.equals(allowedRoom) && !conversationId.equals(sessionUserId)) {
                    log.warn("Cảnh báo: User {} cố tình tham gia conversation của người khác ({})",
                            sessionUserId, conversationId);
                    client.sendEvent("permission_denied", Map.of(
                            "message", "Bạn không có quyền tham gia cuộc trò chuyện này!"
                    ));
                    return;
                }
            }

            String room = "conversation_" + conversationId;
            client.joinRoom(room);
            log.info("Client {} (role={}) joined room {}", client.getSessionId(), role, room);
        });

        socketIOServer.addEventListener("leave_conversation", Map.class, (client, data, ackSender) -> {
            String conversationId = data != null ? (String) data.get("conversationId") : null;
            if (conversationId != null && !conversationId.isBlank()) {
                String room = "conversation_" + conversationId;
                client.leaveRoom(room);
                log.info("Client {} left room {}", client.getSessionId(), room);
            }
        });

        // 4. Gửi tin nhắn chat bảo mật (ngăn mạo danh senderId và role)
        socketIOServer.addEventListener("send_message", SocketChatMessage.class, (client, messageData, ackSender) -> {
            if (messageData == null) {
                return;
            }

            boolean hasContent = messageData.getContent() != null && !messageData.getContent().trim().isEmpty();
            List<String> sanitizedImages = null;
            if (messageData.getImages() != null && !messageData.getImages().isEmpty()) {
                sanitizedImages = messageData.getImages().stream()
                        .filter(url -> url != null && !url.isBlank())
                        .limit(5)
                        .toList();
            }
            boolean hasImages = sanitizedImages != null && !sanitizedImages.isEmpty();

            if (!hasContent && !hasImages) {
                return;
            }

            String sessionUserId = client.get("userId");
            Role sessionRole = client.get("role");
            String sessionUsername = client.get("username");
            String sessionAvatar = client.get("avatar");

            // Danh tính bắt buộc lấy từ Session đã xác thực qua JWT
            Role role = sessionRole != null ? sessionRole : (messageData.getRole() != null ? messageData.getRole() : Role.USER);
            String senderId = sessionUserId != null ? sessionUserId : messageData.getSenderId();
            String username = (messageData.getUsername() != null && !messageData.getUsername().isBlank())
                    ? messageData.getUsername() : (sessionUsername != null ? sessionUsername : "Khách hàng");
            String avatar = (messageData.getAvatar() != null && !messageData.getAvatar().isBlank())
                    ? messageData.getAvatar() : sessionAvatar;

            // Khách hàng chỉ được gửi tin nhắn trong cuộc trò chuyện của chính mình
            if (role == Role.USER && sessionUserId != null) {
                String convId = messageData.getConversationId();
                String allowedRoom = "user_" + sessionUserId;
                if (convId == null || (!convId.equals(allowedRoom) && !convId.equals(sessionUserId))) {
                    log.warn("User {} cố tình gửi tin nhắn vào conversation của người khác: {}",
                            sessionUserId, convId);
                    return;
                }
            }

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

            MessageType messageType = messageData.getType() != null
                    ? messageData.getType()
                    : (hasImages ? MessageType.IMAGE : MessageType.TEXT);

            String trimmedContent = hasContent ? messageData.getContent().trim() : "";

            log.info("Received socket message: from={}, role={}, conv={}, type={}, imagesCount={}",
                    username, role, messageData.getConversationId(), messageType, (sanitizedImages != null ? sanitizedImages.size() : 0));

            SupportMessageRequest request = SupportMessageRequest.builder()
                    .conversationId(messageData.getConversationId())
                    .senderId(senderId)
                    .receiverId(messageData.getReceiverId())
                    .content(trimmedContent)
                    .type(messageType)
                    .images(sanitizedImages)
                    .role(role)
                    .avatar(avatar)
                    .username(username)
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

                    String senderName = username != null && !username.isBlank() ? username : "Khách hàng";
                    String snippet;
                    if (hasImages) {
                        String imgLabel = sanitizedImages.size() > 1 ? "[" + sanitizedImages.size() + " hình ảnh]" : "[Hình ảnh]";
                        snippet = hasContent
                                ? (trimmedContent.length() > 50 ? imgLabel + " " + trimmedContent.substring(0, 47) + "..." : imgLabel + " " + trimmedContent)
                                : imgLabel;
                    } else {
                        snippet = trimmedContent.length() > 60
                                ? trimmedContent.substring(0, 57) + "..."
                                : trimmedContent;
                    }

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
