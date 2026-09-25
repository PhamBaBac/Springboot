package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.UserAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, String> {

    @Query("SELECT l FROM UserAuditLog l WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(l.performedByEmail) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(l.targetUserEmail) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(l.details) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(l.action) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<UserAuditLog> searchLogs(@Param("search") String search, Pageable pageable);
}
