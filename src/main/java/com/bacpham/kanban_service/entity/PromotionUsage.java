package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.PromotionUsageStatus;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Entity ghi nhận lịch sử áp dụng mã giảm giá (Single Source of Truth trong MySQL).
 * Đảm bảo tính toàn vẹn dữ liệu, chống mất mát khi Redis gặp sự cố và hỗ trợ rollback hoàn mã.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "promotion_usages", indexes = {
        @Index(name = "idx_user_promo_status", columnList = "user_id, promotion_code, status"),
        @Index(name = "idx_order_promotion", columnList = "order_id")
})
public class PromotionUsage extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    Promotion promotion;

    @Column(name = "promotion_code", nullable = false)
    String promotionCode;

    @Column(name = "user_id", nullable = false)
    String userId;

    @Column(name = "order_id", nullable = false)
    String orderId;

    @Column(name = "discount_amount")
    Double discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    PromotionUsageStatus status = PromotionUsageStatus.USED;
}
