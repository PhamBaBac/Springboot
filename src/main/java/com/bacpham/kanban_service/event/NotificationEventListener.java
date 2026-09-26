package com.bacpham.kanban_service.event;

import com.bacpham.kanban_service.configuration.socket.NotificationSocketPublisher;
import com.bacpham.kanban_service.dto.response.AdminNotificationResponse;
import com.bacpham.kanban_service.service.IAdminNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventListener {

    private final IAdminNotificationService notificationService;
    private final NotificationSocketPublisher socketPublisher;

    /**
     * Lắng nghe sự kiện NotificationEvent sau khi transaction commit thành công.
     * fallbackExecution = true đảm bảo sự kiện vẫn được xử lý nếu được gọi ngoài @Transactional.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationEvent(NotificationEvent event) {
        if (event == null || event.getRequest() == null) {
            return;
        }

        try {
            log.info("Nhận NotificationEvent: type={}, title='{}', targetUrl='{}'",
                    event.getRequest().type(),
                    event.getRequest().title(),
                    event.getRequest().targetUrl());

            AdminNotificationResponse savedResponse = notificationService.createNotification(event.getRequest());

            socketPublisher.broadcastToAdmin(savedResponse);

        } catch (Exception e) {
            log.error("Lỗi khi xử lý NotificationEvent: {}", e.getMessage(), e);
        }
    }
}
