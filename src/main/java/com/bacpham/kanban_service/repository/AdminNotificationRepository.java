package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.AdminNotification;
import com.bacpham.kanban_service.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface AdminNotificationRepository extends JpaRepository<AdminNotification, String> {

    Optional<AdminNotification> findByIdAndDeletedFalse(String id);

    Page<AdminNotification> findAllByDeletedFalse(Pageable pageable);

    Page<AdminNotification> findAllByDeletedFalseAndIsRead(Boolean isRead, Pageable pageable);

    Page<AdminNotification> findAllByDeletedFalseAndType(NotificationType type, Pageable pageable);

    Page<AdminNotification> findAllByDeletedFalseAndTypeAndIsRead(NotificationType type, Boolean isRead, Pageable pageable);

    long countByIsReadFalseAndDeletedFalse();

    @Modifying
    @Transactional
    @Query("UPDATE AdminNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.isRead = false AND n.deleted = false")
    int markAllAsRead();

    @Modifying
    @Transactional
    @Query("UPDATE AdminNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.id = :id AND n.deleted = false")
    int markAsRead(@Param("id") String id);

    @Modifying
    @Transactional
    @Query("UPDATE AdminNotification n SET n.deleted = true WHERE n.isRead = true AND n.deleted = false")
    int deleteAllRead();
}
