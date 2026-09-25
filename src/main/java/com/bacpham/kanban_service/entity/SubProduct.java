package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "sub_products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubProduct extends BaseModel {
    String size;
    String color;
    Double price;
    Integer qty;
    Integer stock;
    Double cost;
    Double discount;

    @Builder.Default
    @Column(name = "reserved_stock", columnDefinition = "int default 0")
    Integer reservedStock = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    Map<String, String> attributes;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    Product product;

    @OneToMany(mappedBy = "subProduct", cascade = CascadeType.ALL, orphanRemoval = true)
    Set<Review> reviews;

    @OneToMany(mappedBy = "subProduct", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    List<String> images;

    public int getAvailableStock() {
        int s = stock != null ? stock : 0;
        int r = reservedStock != null ? reservedStock : 0;
        return Math.max(0, s - r);
    }

}

