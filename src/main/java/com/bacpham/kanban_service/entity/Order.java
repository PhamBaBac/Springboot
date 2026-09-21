// src/main/java/com/bacpham/kanban_service/entity/Bill.java
package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_deleted_hidden", columnList = "user_id, deleted, customer_hidden"),
        @Index(name = "idx_orders_tracking_code", columnList = "tracking_code"),
        @Index(name = "idx_orders_status_deleted", columnList = "order_status, deleted"),
        @Index(name = "idx_orders_deleted_created", columnList = "deleted, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseModel {
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "address_id")
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    private PaymentType paymentType;

    private double total;
    private String cancelReason;

    @Column(name = "tracking_code")
    private String trackingCode;     // Mã vận đơn GHN (vd: "L5G7S1")

    @Column(name = "shipping_status")
    private String shippingStatus;   // Trạng thái vận chuyển GHN

    @Builder.Default
    @Column(name = "customer_hidden", columnDefinition = "boolean default false")
    private Boolean customerHidden = false;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<Review> reviews;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<Shipment> shipments;
}