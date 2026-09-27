package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.UserNotificationCreateRequest;
import com.bacpham.kanban_service.dto.response.UserNotificationResponse;
import com.bacpham.kanban_service.enums.UserNotificationType;

import java.util.List;

public interface IUserNotificationService {

    List<UserNotificationResponse> getNotifications(String userId, UserNotificationType type);

    long getUnreadCount(String userId);

    void markAsRead(String userId, String notificationId);

    void markAllAsRead(String userId);

    void deleteNotification(String userId, String notificationId);

    void clearAll(String userId);

    UserNotificationResponse createNotification(UserNotificationCreateRequest request);
}
