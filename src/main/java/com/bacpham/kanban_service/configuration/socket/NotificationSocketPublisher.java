package com.bacpham.kanban_service.configuration.socket;

import com.bacpham.kanban_service.dto.response.AdminNotificationResponse;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationSocketPublisher {

    private final SocketIOServer socketIOServer;

    public static final String NOTIFICATION_EVENT = "admin_notification";

    /**
     * Broadcast notification to all connected staff/admins in the ADMIN_CHANNEL
     * and also as a fallback to all connected sockets
     */
    public void broadcastToAdmin(AdminNotificationResponse notification) {
        if (notification == null) return;

        try {
            socketIOServer.getRoomOperations(SupportSocketHandler.ADMIN_CHANNEL)
                    .sendEvent(NOTIFICATION_EVENT, notification);

            log.info("Đã broadcast socket event '{}' tới room {}: id={}, type={}, title='{}'",
                    NOTIFICATION_EVENT,
                    SupportSocketHandler.ADMIN_CHANNEL,
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle());
        } catch (Exception e) {
            log.error("Lỗi khi broadcast socket notification: {}", e.getMessage(), e);
        }
    }
}
