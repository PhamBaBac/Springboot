package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "order_item", indexes = {
        @Index(name = "idx_order_item_order_id", columnList = "order_id"),
        @Index(name = "idx_order_item_sub_product_id", columnList = "sub_product_id")
})
@Getter
@Setter
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

    // --- SNAPSHOT DATA (Bảo toàn dữ liệu lịch sử tại thời điểm đặt hàng) ---
    @Column(name = "product_title")
    String productTitle;

    @Column(name = "sku_code")
    String skuCode;

    @Column(name = "size")
    String size;

    @Column(name = "color")
    String color;

    @Column(name = "image", length = 1000)
    String image;

    @Column(name = "original_price")
    Double originalPrice;

    @Column(name = "cost")
    Double cost;

    @Column(name = "discount_amount")
    Double discountAmount;

    @Column(name = "total_price")
    Double totalPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes_snapshot", columnDefinition = "JSON")
    Map<String, String> attributesSnapshot;
}
