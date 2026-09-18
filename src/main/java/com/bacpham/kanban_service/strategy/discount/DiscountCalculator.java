package com.bacpham.kanban_service.strategy.discount;

import com.bacpham.kanban_service.dto.request.DiscountRequest;
import com.bacpham.kanban_service.enums.PromotionType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Context quản lý các chiến lược tính giảm giá (Strategy Context).
 * Tự động gom các DiscountStrategy bean qua Spring Dependency Injection.
 * Tách biệt hoàn toàn nghiệp vụ tính tiền khỏi OrderService và VNPayService.
 */
@Component
public class DiscountCalculator {

    private final Map<PromotionType, DiscountStrategy> strategies = new EnumMap<>(PromotionType.class);

    public DiscountCalculator(List<DiscountStrategy> strategyList) {
        for (DiscountStrategy strategy : strategyList) {
            strategies.put(strategy.getSupportedType(), strategy);
        }
    }

    /**
     * Tính toán số tiền sau khi áp dụng giảm giá.
     *
     * @param unitPrice Đơn giá gốc
     * @param quantity Số lượng mua
     * @param discount Thông tin giảm giá (null nếu không có)
     * @return DiscountCalculationResult kết quả chiết khấu
     */
    public DiscountCalculationResult calculate(double unitPrice, int quantity, DiscountRequest discount) {
        if (discount == null || discount.getValue() == null || discount.getType() == null) {
            double rawTotal = Math.max(0, unitPrice * quantity);
            return new DiscountCalculationResult(rawTotal, unitPrice);
        }

        DiscountStrategy strategy = strategies.get(discount.getType());
        if (strategy == null) {
            throw new AppException(ErrorCode.INVALID_PROMOTION_TYPE);
        }

        return strategy.calculate(unitPrice, quantity, discount.getValue());
    }
}
