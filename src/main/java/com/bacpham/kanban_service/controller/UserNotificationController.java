package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.UserNotificationResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.enums.UserNotificationType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.IUserNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserNotificationController {

    IUserNotificationService notificationService;
    UserRepository userRepository;

    private User getAuthenticatedUser(Principal principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @GetMapping
    public ApiResponse<List<UserNotificationResponse>> getNotifications(
            @RequestParam(required = false) UserNotificationType type,
            Principal connectedUser
    ) {
        User user = getAuthenticatedUser(connectedUser);
        List<UserNotificationResponse> data = notificationService.getNotifications(user.getId(), type);
        return ApiResponse.<List<UserNotificationResponse>>builder()
                .message("Lấy danh sách thông báo thành công")
                .data(data)
                .build();
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(Principal connectedUser) {
        User user = getAuthenticatedUser(connectedUser);
        long count = notificationService.getUnreadCount(user.getId());
        return ApiResponse.<Long>builder()
                .message("Lấy số lượng thông báo chưa đọc thành công")
                .data(count)
                .build();
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable String id,
            Principal connectedUser
    ) {
        User user = getAuthenticatedUser(connectedUser);
        notificationService.markAsRead(user.getId(), id);
        return ApiResponse.<Void>builder()
                .message("Đã đánh dấu thông báo là đã đọc")
                .build();
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(Principal connectedUser) {
        User user = getAuthenticatedUser(connectedUser);
        notificationService.markAllAsRead(user.getId());
        return ApiResponse.<Void>builder()
                .message("Đã đánh dấu tất cả thông báo là đã đọc")
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(
            @PathVariable String id,
            Principal connectedUser
    ) {
        User user = getAuthenticatedUser(connectedUser);
        notificationService.deleteNotification(user.getId(), id);
        return ApiResponse.<Void>builder()
                .message("Đã xóa thông báo thành công")
                .build();
    }

    @DeleteMapping("/clear-all")
    public ApiResponse<Void> clearAll(Principal connectedUser) {
        User user = getAuthenticatedUser(connectedUser);
        notificationService.clearAll(user.getId());
        return ApiResponse.<Void>builder()
                .message("Đã xóa tất cả thông báo thành công")
                .build();
    }
}
