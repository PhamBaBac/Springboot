package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.PromotionType;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "promotions")
public class Promotion extends BaseModel {

    @Column(nullable = false)
    String title;

    @Column(columnDefinition = "TEXT")
    String description;

    @Column(nullable = false, unique = true)
    String code;

    @Column(nullable = false)
    BigDecimal value;

    Integer numOfAvailable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    PromotionType type;

    @Column(nullable = false)
    LocalDateTime startAt;

    @Column(nullable = false)
    LocalDateTime endAt;

    String imageURL;
}
