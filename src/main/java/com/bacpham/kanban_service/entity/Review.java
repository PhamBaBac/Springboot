package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.bacpham.kanban_service.enums.ReviewStatus;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "review", indexes = {
        @Index(name = "idx_review_subproduct", columnList = "sub_product_id"),
        @Index(name = "idx_review_order", columnList = "order_id"),
        @Index(name = "idx_review_created_by", columnList = "created_by"),
        @Index(name = "idx_review_status", columnList = "status"),
        @Index(name = "idx_review_star", columnList = "star")
})
public class Review extends BaseModel {

    @Column(columnDefinition = "TEXT")
    String comment;

    Integer star;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    List<String> images;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    ReviewStatus status = ReviewStatus.APPROVED;

    @Column(columnDefinition = "TEXT")
    String shopReply;

    Date repliedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replied_by")
    User repliedBy;

    @Builder.Default
    Boolean isFeatured = false;

    String hiddenReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_product_id", nullable = false)
    SubProduct subProduct;

    @PrePersist
    public void prePersistReview() {
        if (status == null) status = ReviewStatus.APPROVED;
        if (isFeatured == null) isFeatured = false;
    }
}
