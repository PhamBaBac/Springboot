package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.PaymentTransactionResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.enums.TransactionStatus;
import com.bacpham.kanban_service.enums.TransactionType;

import java.util.List;

public interface IPaymentTransactionService {

    PaymentTransactionResponse recordTransaction(
            Order order,
            String transactionCode,
            String gatewayTransactionNo,
            PaymentType paymentType,
            TransactionType transactionType,
            Double amount,
            TransactionStatus status,
            String rawResponse,
            String note
    );

    List<PaymentTransactionResponse> getTransactionsByOrderId(String orderId);

    PageResponse<PaymentTransactionResponse> getPagedTransactions(int page, int pageSize);
}
