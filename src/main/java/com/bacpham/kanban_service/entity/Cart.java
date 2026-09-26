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
    Integer count; 

    String size;
    String color;
    String title;

    Double price;

    @Column(name = "stock_quantity")
    Integer qty; 

    @Column(name = "product_id")
    String productId; 

    String image; 
}
