package com.bacpham.kanban_service.configuration.socket;

import com.bacpham.kanban_service.dto.response.AdminNotificationResponse;
import com.bacpham.kanban_service.dto.response.UserNotificationResponse;
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
    public static final String USER_NOTIFICATION_EVENT = "user_notification";

    /**
     * Broadcast notification to a specific connected user in their personal room
     */
    public void sendToUser(String userId, UserNotificationResponse notification) {
        if (userId == null || notification == null) return;

        try {
            String room = "user_" + userId.trim();
            socketIOServer.getRoomOperations(room)
                    .sendEvent(USER_NOTIFICATION_EVENT, notification);

            log.info("Đã broadcast socket event '{}' tới room {}: id={}, title='{}'",
                    USER_NOTIFICATION_EVENT,
                    room,
                    notification.getId(),
                    notification.getTitle());
        } catch (Exception e) {
            log.error("Lỗi khi gửi socket notification tới user {}: {}", userId, e.getMessage(), e);
        }
    }

    public void sendUnreadCountToUser(String userId, long unreadCount) {
        if (userId == null) return;
        try {
            String room = "user_" + userId.trim();
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("unreadCount", unreadCount);
            socketIOServer.getRoomOperations(room)
                    .sendEvent("user_unread_count", payload);
            log.info("Đã broadcast socket event 'user_unread_count' tới room {}: unreadCount={}", room, unreadCount);
        } catch (Exception e) {
            log.error("Lỗi khi broadcast socket user unread count: {}", e.getMessage(), e);
        }
    }

    /**
     * Broadcast realtime order status update to customer in their personal room
     */
    public void sendOrderStatusUpdateToUser(String userId, String orderId, String newStatus, String cancelReason) {
        if (userId == null) return;
        try {
            String room = "user_" + userId.trim();
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("orderId", orderId);
            payload.put("orderStatus", newStatus);
            if (cancelReason != null && !cancelReason.isBlank()) {
                payload.put("cancelReason", cancelReason.trim());
            }
            socketIOServer.getRoomOperations(room)
                    .sendEvent("order_status_updated", payload);
            log.info("Đã broadcast socket event 'order_status_updated' tới room {}: orderId={}, newStatus={}",
                    room, orderId, newStatus);
        } catch (Exception e) {
            log.error("Lỗi khi gửi socket order_status_updated tới user {}: {}", userId, e.getMessage(), e);
        }
    }

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
