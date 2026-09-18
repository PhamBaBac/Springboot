package com.bacpham.kanban_service.strategy.payment;

import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * PaymentStrategyRegistry quản lý và định tuyến các cổng thanh toán.
 * Áp dụng Registry Pattern kết hợp với Spring Dependency Injection:
 * Mọi class implements PaymentStrategy đều được Spring tự động scan và đăng ký vào đây.
 * Tuân thủ Open/Closed Principle (OCP):
 * Để thêm cổng thanh toán mới, chỉ cần tạo Strategy mới, Registry tự động nhận diện mà không cần sửa code.
 */
@Component
@Slf4j
public class PaymentStrategyRegistry {

    private final Map<PaymentType, PaymentStrategy> strategies = new EnumMap<>(PaymentType.class);

    public PaymentStrategyRegistry(List<PaymentStrategy> strategyList) {
        for (PaymentStrategy strategy : strategyList) {
            strategies.put(strategy.getPaymentType(), strategy);
            log.info("Registered payment strategy: [{}] -> {}", strategy.getPaymentType(), strategy.getClass().getSimpleName());
        }
    }

    /**
     * Lấy Strategy theo PaymentType enum.
     */
    public PaymentStrategy getStrategy(PaymentType paymentType) {
        PaymentStrategy strategy = strategies.get(paymentType);
        if (strategy == null) {
            log.error("No payment strategy registered for type: {}", paymentType);
            throw new AppException(ErrorCode.UNCATEGORIZED);
        }
        return strategy;
    }

    /**
     * Lấy Strategy theo chuỗi ký tự (hỗ trợ case-insensitive, fallback sang VNPAY nếu rỗng).
     */
    public PaymentStrategy getStrategyOrDefault(String paymentTypeStr) {
        if (paymentTypeStr == null || paymentTypeStr.trim().isEmpty()) {
            return getStrategy(PaymentType.VNPAY);
        }

        try {
            PaymentType paymentType = PaymentType.valueOf(paymentTypeStr.trim().toUpperCase());
            PaymentStrategy strategy = strategies.get(paymentType);
            if (strategy != null) {
                return strategy;
            }
        } catch (IllegalArgumentException ignored) {
            // Không khớp với enum, tiếp tục kiểm tra fallback
        }

        // Fallback mặc định về VNPay để bảo đảm tương thích ngược
        log.warn("Payment type '{}' not recognized or registered. Falling back to VNPAY.", paymentTypeStr);
        return getStrategy(PaymentType.VNPAY);
    }

    public boolean hasStrategy(PaymentType paymentType) {
        return strategies.containsKey(paymentType);
    }

    public Set<PaymentType> getSupportedTypes() {
        return Collections.unmodifiableSet(strategies.keySet());
    }
}
