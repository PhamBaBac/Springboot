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

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminNotificationRepository extends JpaRepository<AdminNotification, String> {

    Optional<AdminNotification> findByIdAndDeletedFalse(String id);

    Page<AdminNotification> findAllByDeletedFalse(Pageable pageable);

    Page<AdminNotification> findAllByDeletedFalseAndIsRead(Boolean isRead, Pageable pageable);

    Page<AdminNotification> findAllByDeletedFalseAndType(NotificationType type, Pageable pageable);

    Page<AdminNotification> findAllByDeletedFalseAndTypeAndIsRead(NotificationType type, Boolean isRead, Pageable pageable);

    @Query(
        value = """
            SELECT n FROM AdminNotification n
            WHERE (n.deleted = false OR n.deleted IS NULL)
              AND (:type IS NULL OR n.type = :type)
              AND (:isRead IS NULL OR n.isRead = :isRead)
              AND (:search IS NULL OR :search = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(n.content) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
        countQuery = """
            SELECT COUNT(n) FROM AdminNotification n
            WHERE (n.deleted = false OR n.deleted IS NULL)
              AND (:type IS NULL OR n.type = :type)
              AND (:isRead IS NULL OR n.isRead = :isRead)
              AND (:search IS NULL OR :search = '' OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(n.content) LIKE LOWER(CONCAT('%', :search, '%')))
        """
    )
    Page<AdminNotification> filterNotifications(
            @Param("type") NotificationType type,
            @Param("isRead") Boolean isRead,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT COUNT(n) FROM AdminNotification n WHERE (n.deleted = false OR n.deleted IS NULL)")
    long countTotal();

    @Query("SELECT COUNT(n) FROM AdminNotification n WHERE (n.deleted = false OR n.deleted IS NULL) AND n.isRead = false")
    long countUnread();

    @Query("SELECT COUNT(n) FROM AdminNotification n WHERE (n.deleted = false OR n.deleted IS NULL) AND n.type IN :types")
    long countByTypes(@Param("types") List<NotificationType> types);

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
