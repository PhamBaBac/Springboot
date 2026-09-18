package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.PaymentResponse;
import com.bacpham.kanban_service.service.IPaymeentService;
import com.bacpham.kanban_service.strategy.payment.PaymentCallbackResult;
import com.bacpham.kanban_service.strategy.payment.VNPayPaymentStrategy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Adapter / Delegate cho IVNPayService để giữ tương thích ngược,
 * ủy quyền toàn bộ xử lý sang VNPayPaymentStrategy.
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymeentService {

    private final VNPayPaymentStrategy vnPayPaymentStrategy;

    @Override
    public PaymentResponse createPaymentUrl(OrderCreateRequest request, String userId, HttpServletRequest httpRequest) {
        return vnPayPaymentStrategy.createPaymentUrl(request, userId, httpRequest);
    }

    @Override
    public VNPayCallbackResult handleCallback(Map<String, String> params) {
        PaymentCallbackResult res = vnPayPaymentStrategy.handleCallback(params);
        return new VNPayCallbackResult(
                res.success(),
                res.signatureValid(),
                res.message(),
                res.transactionNo(),
                res.payDate(),
                res.txnRef(),
                res.userId(),
                res.orderRequest(),
                res.alreadyProcessed()
        );
    }
}