// src/main/java/com/bacpham/kanban_service/entity/Bill.java
package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.BillStatus;
import com.bacpham.kanban_service.enums.PaymentStatus;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Bill  extends BaseModel {
    private Double total;

    @Enumerated(EnumType.STRING)
    private BillStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillItem> items;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    private PaymentType paymentType = PaymentType.COD;
}