package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;


@Entity
@Table(name = "cart")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Cart extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_product_id", nullable = false)
    SubProduct subProduct;

    @Column(nullable = false)
    Integer count; // Số lượng muốn mua

    String size;
    String color;
    String title;

    Double price;

    @Column(name = "stock_quantity")
    Integer qty; // Số lượng còn lại trong kho tại thời điểm thêm

    @Column(name = "product_id")
    String productId; // UUID của Product

    String image; // Chỉ lưu 1 ảnh đại diện
}
