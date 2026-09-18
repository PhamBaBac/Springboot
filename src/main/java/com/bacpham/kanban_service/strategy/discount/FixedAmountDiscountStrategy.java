package com.bacpham.kanban_service.strategy.discount;

import com.bacpham.kanban_service.enums.PromotionType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Chiến lược giảm giá theo số tiền cố định (ví dụ: giảm 50.000đ).
 */
@Component
public class FixedAmountDiscountStrategy implements DiscountStrategy {

    @Override
    public PromotionType getSupportedType() {
        return PromotionType.DISCOUNT;
    }

    @Override
    public DiscountCalculationResult calculate(double unitPrice, int quantity, String discountValue) {
        try {
            double discountAmount = Double.parseDouble(discountValue);
            double itemTotal = Math.max(0, (unitPrice * quantity) - discountAmount);
            double unitPriceAfterDiscount = quantity > 0 ? itemTotal / quantity : 0;
            return new DiscountCalculationResult(itemTotal, unitPriceAfterDiscount);
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.INVALID_PROMOTION_VALUE);
        }
    }
}
