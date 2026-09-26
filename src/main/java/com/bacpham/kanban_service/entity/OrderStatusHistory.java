package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Bảng OrderStatusHistory: Ghi nhận lịch sử chuyển trạng thái đơn hàng (Audit Trail).
 * Phục vụ truy vết khi có khiếu nại, đối soát giữa Khách hàng - Admin - Vận chuyển.
 */
@Entity
@Table(name = "order_status_history", indexes = {
        @Index(name = "idx_status_history_order_id", columnList = "order_id"),
        @Index(name = "idx_status_history_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderStatusHistory extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 50)
    OrderStatus toStatus;

    @Column(name = "changed_by_id", length = 100)
    String changedById;

    @Column(name = "changed_by_role", length = 50)
    String changedByRole; 

    @Column(name = "reason", length = 500)
    String reason;

    @Column(name = "metadata", columnDefinition = "TEXT")
    String metadata; 
}
