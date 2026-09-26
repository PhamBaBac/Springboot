package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.enums.TransactionStatus;
import com.bacpham.kanban_service.enums.TransactionType;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Bảng PaymentTransaction: Sổ cái ghi nhận dòng tiền (Transaction Ledger).
 * Tuân thủ nguyên tắc Append-only: không update đè số tiền, mỗi biến động tài chính
 * (thanh toán, hoàn tiền, thu hộ COD) là một dòng dữ liệu mới phục vụ kế toán & đối soát.
 */
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_txn_order_id", columnList = "order_id"),
        @Index(name = "idx_txn_code", columnList = "transaction_code"),
        @Index(name = "idx_txn_gateway_no", columnList = "gateway_transaction_no"),
        @Index(name = "idx_txn_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentTransaction extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    Order order;

    @Column(name = "transaction_code", nullable = false, length = 100)
    String transactionCode; 

    @Column(name = "gateway_transaction_no", length = 150)
    String gatewayTransactionNo; 

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 50)
    PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    TransactionType transactionType; 
    

    @Column(name = "amount", nullable = false)
    Double amount;

    @Builder.Default
    @Column(name = "currency", length = 10)
    String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    TransactionStatus status;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    String rawResponse;

    @Column(name = "note", length = 500)
    String note;
}
