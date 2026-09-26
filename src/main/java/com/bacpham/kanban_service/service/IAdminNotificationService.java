package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.AdminNotificationRequest;
import com.bacpham.kanban_service.dto.response.AdminNotificationResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.enums.NotificationType;

public interface IAdminNotificationService {

    PageResponse<AdminNotificationResponse> getNotifications(int page, int size, NotificationType type, Boolean unreadOnly);

    long getUnreadCount();

    AdminNotificationResponse markAsRead(String id);

    int markAllAsRead();

    void deleteNotification(String id);

    int deleteAllRead();

    AdminNotificationResponse createNotification(AdminNotificationRequest request);
}
