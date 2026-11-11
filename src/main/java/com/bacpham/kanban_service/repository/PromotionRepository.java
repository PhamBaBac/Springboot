package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.dto.response.PromotionResponse;
import com.bacpham.kanban_service.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, String> {

    Optional<Promotion> findByCode(String code);
    Optional<Promotion> findByCodeAndDeletedFalse(String code);
    boolean existsByCodeAndDeletedFalse(String code);
    List<Promotion> findAllByDeletedFalse();

    @Query("SELECT p FROM Promotion p WHERE p.deleted = false AND p.startAt <= :now AND p.endAt >= :now AND (p.numOfAvailable IS NULL OR p.numOfAvailable > 0) ORDER BY p.startAt DESC")
    List<Promotion> findActivePromotions(@Param("now") LocalDateTime now);

    @Query("SELECT p FROM Promotion p WHERE p.deleted = false AND (p.isCampaign = true OR p.code LIKE 'CAMPAIGN_%') AND p.startAt <= :now AND p.endAt >= :now AND (p.numOfAvailable IS NULL OR p.numOfAvailable > 0) ORDER BY p.startAt DESC")
    List<Promotion> findActiveCampaigns(@Param("now") LocalDateTime now);
}
