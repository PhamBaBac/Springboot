package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.UserNotification;
import com.bacpham.kanban_service.enums.UserNotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, String> {

    List<UserNotification> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(String userId);

    List<UserNotification> findByUserIdAndTypeAndDeletedFalseOrderByCreatedAtDesc(String userId, UserNotificationType type);

    long countByUserIdAndIsReadFalseAndDeletedFalse(String userId);

    Optional<UserNotification> findByIdAndUserIdAndDeletedFalse(String id, String userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.user.id = :userId AND n.isRead = false AND n.deleted = false")
    void markAllAsRead(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.deleted = true WHERE n.user.id = :userId AND n.deleted = false")
    void clearAll(@Param("userId") String userId);
}
