package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.AdminNotificationRequest;
import com.bacpham.kanban_service.dto.response.AdminNotificationResponse;
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

    @Override
    public PageResponse<AdminNotificationResponse> getNotifications(int page, int size, NotificationType type, Boolean unreadOnly) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminNotification> pageResult;

        if (type != null && Boolean.TRUE.equals(unreadOnly)) {
            pageResult = repository.findAllByDeletedFalseAndTypeAndIsRead(type, false, pageable);
        } else if (type != null) {
            pageResult = repository.findAllByDeletedFalseAndType(type, pageable);
        } else if (Boolean.TRUE.equals(unreadOnly)) {
            pageResult = repository.findAllByDeletedFalseAndIsRead(false, pageable);
        } else {
            pageResult = repository.findAllByDeletedFalse(pageable);
        }

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

        return mapper.toAdminNotificationResponse(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead() {
        return repository.markAllAsRead();
    }

    @Override
    @Transactional
    public void deleteNotification(String id) {
        AdminNotification notification = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.setDeleted(true);
        repository.save(notification);
    }

    @Override
    @Transactional
    public int deleteAllRead() {
        return repository.deleteAllRead();
    }

    @Override
    @Transactional
    public AdminNotificationResponse createNotification(AdminNotificationRequest request) {
        AdminNotification notification = mapper.toAdminNotification(request);
        AdminNotification saved = repository.save(notification);
        log.info("Đã tạo thông báo admin mới: id={}, type={}, title={}", saved.getId(), saved.getType(), saved.getTitle());
        return mapper.toAdminNotificationResponse(saved);
    }
}
