package com.bacpham.kanban_service.repository;

import com.bacpham.kanban_service.entity.PromotionUsage;
import com.bacpham.kanban_service.enums.PromotionUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, String> {

    boolean existsByUserIdAndPromotionCodeAndStatus(String userId, String promotionCode, PromotionUsageStatus status);

    List<PromotionUsage> findByOrderId(String orderId);

    Optional<PromotionUsage> findByOrderIdAndStatus(String orderId, PromotionUsageStatus status);

    List<PromotionUsage> findAllByPromotionCode(String promotionCode);
}
