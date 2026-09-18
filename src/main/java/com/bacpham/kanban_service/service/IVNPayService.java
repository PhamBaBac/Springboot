package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.PaymentResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Interface cho VNPay Payment Service.
 * Tuan thu SRP: PaymentVNPayController chi xu ly HTTP request/response.
 * Toan bo business logic tao URL, verify hash duoc chuyen xuong day.
 * Tuan thu DIP: Controller phu thuoc vao interface nay.
 */
public interface IVNPayService {

    /**
     * Tao URL thanh toan VNPay tu don hang.
     * @param request Chi tiet cac san pham can thanh toan
     * @param userId ID nguoi dung thuc hien thanh toan
     * @param httpRequest HTTP request de lay IP address
     * @return PaymentResponse chua paymentUrl de redirect
     */
    PaymentResponse createPaymentUrl(OrderCreateRequest request, String userId, HttpServletRequest httpRequest);

    /**
     * Xu ly callback tu VNPay sau khi thanh toan.
     * @param params Cac tham so VNPay tra ve (vnp_ResponseCode, vnp_TxnRef, ...)
     * @return VNPayCallbackResult chua ket qua xu ly
     */
    VNPayCallbackResult handleCallback(Map<String, String> params);

    /**
     * Ket qua xu ly callback tu VNPay.
     */
    record VNPayCallbackResult(
            boolean success,
            boolean signatureValid,
            String message,
            String transactionNo,
            String payDate,
            String txnRef
    ) {}
}