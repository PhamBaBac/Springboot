package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.configuration.socket.NotificationSocketPublisher;
import com.bacpham.kanban_service.dto.request.AdminNotificationRequest;
import com.bacpham.kanban_service.dto.response.AdminNotificationResponse;
import com.bacpham.kanban_service.dto.response.AdminNotificationStatsResponse;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.entity.AdminNotification;
import com.bacpham.kanban_service.enums.NotificationType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.AdminNotificationMapper;
import com.bacpham.kanban_service.repository.AdminNotificationRepository;
import com.bacpham.kanban_service.service.IAdminNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AdminNotificationServiceImpl implements IAdminNotificationService {

    AdminNotificationRepository repository;
    AdminNotificationMapper mapper;
    NotificationSocketPublisher socketPublisher;

    @Override
    public PageResponse<AdminNotificationResponse> getNotifications(int page, int size, NotificationType type, Boolean isRead, String search) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<AdminNotification> pageResult = repository.filterNotifications(type, isRead, cleanSearch, pageable);

        List<AdminNotificationResponse> items = pageResult.getContent().stream()
                .map(mapper::toAdminNotificationResponse)
                .toList();

        return PageResponse.<AdminNotificationResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .data(items)
                .build();
    }

    @Override
    public AdminNotificationStatsResponse getNotificationStats() {
        long total = repository.countTotal();
        long unread = repository.countUnread();
        long orders = repository.countByTypes(List.of(NotificationType.ORDER_NEW, NotificationType.ORDER_CANCEL));
        long stock = repository.countByTypes(List.of(NotificationType.LOW_STOCK, NotificationType.OUT_OF_STOCK));

        return AdminNotificationStatsResponse.builder()
                .total(total)
                .unread(unread)
                .orders(orders)
                .stock(stock)
                .build();
    }

    @Override
    public long getUnreadCount() {
        return repository.countByIsReadFalseAndDeletedFalse();
    }

    @Override
    @Transactional
    public AdminNotificationResponse markAsRead(String id) {
        AdminNotification notification = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(new Date());
            notification = repository.save(notification);
        }

        AdminNotificationResponse response = mapper.toAdminNotificationResponse(notification);
        long unreadCount = repository.countByIsReadFalseAndDeletedFalse();
        socketPublisher.broadcastNotificationRead(id, unreadCount);
        return response;
    }

    @Override
    @Transactional
    public int markAllAsRead() {
        int updated = repository.markAllAsRead();
        long unreadCount = repository.countByIsReadFalseAndDeletedFalse();
        socketPublisher.broadcastNotificationReadAll(unreadCount);
        return updated;
    }

    @Override
    @Transactional
    public void deleteNotification(String id) {
        AdminNotification notification = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.setDeleted(true);
        repository.save(notification);
        long unreadCount = repository.countByIsReadFalseAndDeletedFalse();
        socketPublisher.broadcastNotificationDeleted(id, unreadCount);
    }

    @Override
    @Transactional
    public int deleteAllRead() {
        int deleted = repository.deleteAllRead();
        long unreadCount = repository.countByIsReadFalseAndDeletedFalse();
        socketPublisher.broadcastNotificationClearRead(unreadCount);
        return deleted;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminNotificationResponse createNotification(AdminNotificationRequest request) {
        AdminNotification notification = mapper.toAdminNotification(request);
        if (notification.getDeleted() == null) {
            notification.setDeleted(false);
        }
        if (notification.getIsRead() == null) {
            notification.setIsRead(false);
        }
        AdminNotification saved = repository.saveAndFlush(notification);
        log.info("Đã tạo thông báo admin mới: id={}, type={}, title={}", saved.getId(), saved.getType(), saved.getTitle());
        return mapper.toAdminNotificationResponse(saved);
    }
}
