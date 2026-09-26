package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.AdminNotificationRequest;
import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.AdminNotificationResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.enums.NotificationType;
import com.bacpham.kanban_service.service.IAdminNotificationService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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

    @GetMapping
    public ApiResponse<PageResponse<AdminNotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly
    ) {
        PageResponse<AdminNotificationResponse> response = notificationService.getNotifications(page, size, type, unreadOnly);
        return ApiResponse.<PageResponse<AdminNotificationResponse>>builder()
                .data(response)
                .message("Lấy danh sách thông báo thành công")
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
        return ApiResponse.<AdminNotificationResponse>builder()
                .data(response)
                .message("Tạo thông báo thành công")
                .build();
    }
}
