package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.enums.TransactionStatus;
import com.bacpham.kanban_service.enums.TransactionType;
import com.bacpham.kanban_service.utils.formater.time.DateGenerator;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentTransactionResponse {
    String id;
    String orderId;
    String transactionCode;
    String gatewayTransactionNo;
    PaymentType paymentType;
    TransactionType transactionType;
    Double amount;
    String currency;
    TransactionStatus status;
    String rawResponse;
    String note;

    @JsonSerialize(using = DateGenerator.class)
    Date createdAt;
}
