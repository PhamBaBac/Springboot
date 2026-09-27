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

    public void broadcastNotificationRead(String id, long unreadCount) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("id", id);
            payload.put("unreadCount", unreadCount);
            socketIOServer.getRoomOperations(SupportSocketHandler.ADMIN_CHANNEL)
                    .sendEvent("admin_notification_read", payload);
            log.info("Đã broadcast socket event 'admin_notification_read' id={}, unreadCount={}", id, unreadCount);
        } catch (Exception e) {
            log.error("Lỗi khi broadcast socket admin_notification_read: {}", e.getMessage(), e);
        }
    }

    public void broadcastNotificationReadAll(long unreadCount) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("unreadCount", unreadCount);
            socketIOServer.getRoomOperations(SupportSocketHandler.ADMIN_CHANNEL)
                    .sendEvent("admin_notification_read_all", payload);
            log.info("Đã broadcast socket event 'admin_notification_read_all' unreadCount={}", unreadCount);
        } catch (Exception e) {
            log.error("Lỗi khi broadcast socket admin_notification_read_all: {}", e.getMessage(), e);
        }
    }

    public void broadcastNotificationDeleted(String id, long unreadCount) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("id", id);
            payload.put("unreadCount", unreadCount);
            socketIOServer.getRoomOperations(SupportSocketHandler.ADMIN_CHANNEL)
                    .sendEvent("admin_notification_deleted", payload);
            log.info("Đã broadcast socket event 'admin_notification_deleted' id={}, unreadCount={}", id, unreadCount);
        } catch (Exception e) {
            log.error("Lỗi khi broadcast socket admin_notification_deleted: {}", e.getMessage(), e);
        }
    }

    public void broadcastNotificationClearRead(long unreadCount) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("unreadCount", unreadCount);
            socketIOServer.getRoomOperations(SupportSocketHandler.ADMIN_CHANNEL)
                    .sendEvent("admin_notification_clear_read", payload);
            log.info("Đã broadcast socket event 'admin_notification_clear_read' unreadCount={}", unreadCount);
        } catch (Exception e) {
            log.error("Lỗi khi broadcast socket admin_notification_clear_read: {}", e.getMessage(), e);
        }
    }
}
