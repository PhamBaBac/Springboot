package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.UserNotificationCreateRequest;
import com.bacpham.kanban_service.dto.response.UserNotificationResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.entity.UserNotification;
import com.bacpham.kanban_service.enums.UserNotificationType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.UserNotificationMapper;
import com.bacpham.kanban_service.repository.UserNotificationRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.IUserNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserNotificationServiceImpl implements IUserNotificationService {

    UserNotificationRepository notificationRepository;
    UserRepository userRepository;
    UserNotificationMapper notificationMapper;

    @Override
    public List<UserNotificationResponse> getNotifications(String userId, UserNotificationType type) {
        List<UserNotification> notifications;
        if (type == null) {
            notifications = notificationRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);
        } else {
            notifications = notificationRepository.findByUserIdAndTypeAndDeletedFalseOrderByCreatedAtDesc(userId, type);
        }
        return notificationMapper.toResponseList(notifications);
    }

    @Override
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalseAndDeletedFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(String userId, String notificationId) {
        UserNotification notification = notificationRepository
                .findByIdAndUserIdAndDeletedFalse(notificationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(new Date());
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Override
    @Transactional
    public void deleteNotification(String userId, String notificationId) {
        UserNotification notification = notificationRepository
                .findByIdAndUserIdAndDeletedFalse(notificationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        notification.setDeleted(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void clearAll(String userId) {
        notificationRepository.clearAll(userId);
    }

    @Override
    @Transactional
    public UserNotificationResponse createNotification(UserNotificationCreateRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserNotification notification = UserNotification.builder()
                .user(user)
                .title(request.getTitle())
                .content(request.getContent())
                .type(request.getType())
                .targetUrl(request.getTargetUrl())
                .referenceId(request.getReferenceId())
                .isRead(false)
                .build();

        UserNotification saved = notificationRepository.save(notification);
        return notificationMapper.toResponse(saved);
    }
}
