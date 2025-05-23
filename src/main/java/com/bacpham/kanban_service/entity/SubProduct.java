package com.bacpham.kanban_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "sub_products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String size;
    String color;
    Double price;
    Integer qty;
    Double cost;
    Double discount;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    Product product;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    List<String> images;

    @Column(nullable = false)
    Boolean deleted = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false, updatable = false)
    Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    Date updatedAt;
    @PrePersist
    protected void onCreate() {
        Date now = new Date();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = new Date();
    }
}

