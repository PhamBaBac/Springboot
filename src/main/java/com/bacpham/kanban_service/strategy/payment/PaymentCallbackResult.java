package com.bacpham.kanban_service.strategy.payment;

import com.bacpham.kanban_service.dto.request.OrderCreateRequest;

/**
 * Kết quả chuẩn hóa sau khi cổng thanh toán xử lý callback/return (Thread-safe & Idempotent).
 * Dùng chung cho tất cả các cổng thanh toán (VNPay, MoMo, ZaloPay, Stripe...).
 */
public record PaymentCallbackResult(
        boolean success,
        boolean signatureValid,
        String message,
        String transactionNo,
        String payDate,
        String txnRef,
        String userId,
        OrderCreateRequest orderRequest,
        boolean alreadyProcessed
) {}
