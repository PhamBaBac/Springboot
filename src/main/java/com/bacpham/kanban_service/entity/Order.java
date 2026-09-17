// src/main/java/com/bacpham/kanban_service/entity/Bill.java
package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "orders") // Changed from "order" to "orders"
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
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;

    private double total;
    private String cancelReason;
    private String trackingCode;     // Mã vận đơn GHN (vd: "L5G7S1")
    private String shippingStatus;   // Trạng thái vận chuyển GHN

    @Builder.Default
    @Column(name = "customer_hidden", columnDefinition = "boolean default false")
    private Boolean customerHidden = false;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    @OneToMany (mappedBy = "order", cascade = CascadeType.ALL)
    private List<Review> reviews;
}