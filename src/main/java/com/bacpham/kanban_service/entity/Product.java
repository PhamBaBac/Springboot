package com.bacpham.kanban_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Set;
import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, unique = true, length = 255)
    String title;

    String slug;

    @Column(columnDefinition = "TEXT")
    String description;

    @Lob
    @Column(columnDefinition = "TEXT")
    String content;

    @Column(nullable = false)
    Boolean deleted = false;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    Set<SubProduct> subProducts;

    @ManyToMany
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    Set<Category> categories;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    List<String> images;

    @ManyToOne
    @JoinColumn(name = "supplier_id", nullable = false)
    Supplier supplier;

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
