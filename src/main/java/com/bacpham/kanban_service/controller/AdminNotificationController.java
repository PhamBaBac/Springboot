package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.configuration.socket.NotificationSocketPublisher;
import com.bacpham.kanban_service.dto.request.AdminNotificationRequest;
import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.AdminNotificationResponse;
import com.bacpham.kanban_service.dto.response.AdminNotificationStatsResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.enums.NotificationPriority;
import com.bacpham.kanban_service.enums.NotificationType;
import com.bacpham.kanban_service.event.NotificationEvent;
import com.bacpham.kanban_service.service.IAdminNotificationService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AdminNotificationController {

    IAdminNotificationService notificationService;
    NotificationSocketPublisher socketPublisher;
    ApplicationEventPublisher eventPublisher;

    @GetMapping
    public ApiResponse<PageResponse<AdminNotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) String search
    ) {
        Boolean resolvedIsRead = isRead;
        if (resolvedIsRead == null && Boolean.TRUE.equals(unreadOnly)) {
            resolvedIsRead = false;
        }
        PageResponse<AdminNotificationResponse> response = notificationService.getNotifications(page, size, type, resolvedIsRead, search);
        return ApiResponse.<PageResponse<AdminNotificationResponse>>builder()
                .data(response)
                .message("Lấy danh sách thông báo thành công")
                .build();
    }

    @GetMapping("/stats")
    public ApiResponse<AdminNotificationStatsResponse> getNotificationStats() {
        AdminNotificationStatsResponse stats = notificationService.getNotificationStats();
        return ApiResponse.<AdminNotificationStatsResponse>builder()
                .data(stats)
                .message("Lấy thống kê thông báo thành công")
                .build();
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount() {
        long count = notificationService.getUnreadCount();
        return ApiResponse.<Long>builder()
                .data(count)
                .message("Lấy số lượng thông báo chưa đọc thành công")
                .build();
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<AdminNotificationResponse> markAsRead(@PathVariable String id) {
        AdminNotificationResponse response = notificationService.markAsRead(id);
        return ApiResponse.<AdminNotificationResponse>builder()
                .data(response)
                .message("Đánh dấu đã đọc thông báo thành công")
                .build();
    }

    @PatchMapping("/read-all")
    public ApiResponse<Integer> markAllAsRead() {
        int updatedCount = notificationService.markAllAsRead();
        return ApiResponse.<Integer>builder()
                .data(updatedCount)
                .message("Đã đánh dấu tất cả thông báo là đã đọc")
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(@PathVariable String id) {
        notificationService.deleteNotification(id);
        return ApiResponse.<Void>builder()
                .message("Xóa thông báo thành công")
                .build();
    }

    @DeleteMapping("/clear-all")
    public ApiResponse<Integer> clearAllRead() {
        int deletedCount = notificationService.deleteAllRead();
        return ApiResponse.<Integer>builder()
                .data(deletedCount)
                .message("Đã dọn dẹp các thông báo đã đọc")
                .build();
    }

    @PostMapping
    public ApiResponse<AdminNotificationResponse> createNotification(@Valid @RequestBody AdminNotificationRequest request) {
        AdminNotificationResponse response = notificationService.createNotification(request);
        socketPublisher.broadcastToAdmin(response);
        return ApiResponse.<AdminNotificationResponse>builder()
                .data(response)
                .message("Tạo thông báo thành công")
                .build();
    }

    @PostMapping("/simulate")
    public ApiResponse<String> simulateEvent(@RequestParam(defaultValue = "ORDER_NEW") NotificationType type) {
        String testId = "TEST-" + (System.currentTimeMillis() % 100000);
        NotificationEvent event = switch (type) {
            case ORDER_NEW -> NotificationEvent.of(
                    this,
                    NotificationType.ORDER_NEW,
                    "Đơn hàng mới #" + testId,
                    "Khách hàng Nguyễn Văn A vừa đặt đơn hàng #" + testId + " trị giá 1,250,000 đ",
                    NotificationPriority.HIGH,
                    "/orders?id=" + testId + "&status=PENDING",
                    testId
            );
            case ORDER_CANCEL -> NotificationEvent.of(
                    this,
                    NotificationType.ORDER_CANCEL,
                    "Đơn hàng đã bị hủy #" + testId,
                    "Đơn hàng #" + testId + " đã bị khách hàng hủy. Lý do: Thay đổi địa chỉ nhận hàng",
                    NotificationPriority.URGENT,
                    "/orders?id=" + testId + "&status=CANCELLED",
                    testId
            );
            case LOW_STOCK -> NotificationEvent.of(
                    this,
                    NotificationType.LOW_STOCK,
                    "Cảnh báo sắp hết hàng",
                    "Biến thể Áo thun nam Polo - Đen / L chỉ còn lại 3 sản phẩm trong kho.",
                    NotificationPriority.HIGH,
                    "/inventory",
                    testId
            );
            case OUT_OF_STOCK -> NotificationEvent.of(
                    this,
                    NotificationType.OUT_OF_STOCK,
                    "Sản phẩm đã hết hàng!",
                    "Biến thể Giày Sneaker Retro - Trắng / 42 đã hết hàng trong kho.",
                    NotificationPriority.URGENT,
                    "/inventory",
                    testId
            );
            case SUPPORT_MESSAGE -> NotificationEvent.of(
                    this,
                    NotificationType.SUPPORT_MESSAGE,
                    "Tin nhắn hỗ trợ từ Trần Thị B",
                    "Chào shop, đơn hàng của em hôm nay đã được gửi đi chưa ạ?",
                    NotificationPriority.NORMAL,
                    "/support",
                    testId
            );
            case NEW_REVIEW -> NotificationEvent.of(
                    this,
                    NotificationType.NEW_REVIEW,
                    "Đánh giá mới từ khách hàng",
                    "Lê Hoàng C đã gửi đánh giá (5⭐) cho 'Áo Khoác Bomber Kaki'",
                    NotificationPriority.NORMAL,
                    "/inventory",
                    testId
            );
            case SYSTEM_ALERT -> NotificationEvent.of(
                    this,
                    NotificationType.SYSTEM_ALERT,
                    "Cảnh báo hệ thống",
                    "Hệ thống sẽ tiến hành bảo trì định kỳ hoặc có thông báo quan trọng.",
                    NotificationPriority.URGENT,
                    "/settings",
                    testId
            );
        };

        eventPublisher.publishEvent(event);
        log.info("Simulated event triggered for type: {}", type);

        return ApiResponse.<String>builder()
                .data("Đã mô phỏng sự kiện " + type.name() + " thành công")
                .message("Kích hoạt sự kiện thông báo thử nghiệm thành công")
                .build();
    }
}
