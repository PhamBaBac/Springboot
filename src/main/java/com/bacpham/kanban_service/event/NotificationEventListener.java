package com.bacpham.kanban_service.event;

import com.bacpham.kanban_service.service.IAdminNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventListener {

    private final IAdminNotificationService notificationService;

    /**
     * Lắng nghe sự kiện NotificationEvent sau khi transaction commit thành công.
     * Chạy bất đồng bộ (@Async) trên thread pool riêng để không chặn thread của request chính.
     */
    @Async("taskExecutor")
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

            notificationService.createNotification(event.getRequest());

        } catch (Exception e) {
            log.error("Lỗi khi xử lý NotificationEvent: {}", e.getMessage(), e);
        }
    }
}
