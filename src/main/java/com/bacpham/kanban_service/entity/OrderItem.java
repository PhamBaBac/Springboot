package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItem extends BaseModel {
    Integer quantity;
    Double priceAtOrderTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_product_id")
    SubProduct subProduct;
}
