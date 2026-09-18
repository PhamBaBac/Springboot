package com.bacpham.kanban_service.strategy.discount;

import com.bacpham.kanban_service.enums.PromotionType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Chiến lược giảm giá theo phần trăm (ví dụ: giảm 10%, 20%).
 */
@Component
public class PercentageDiscountStrategy implements DiscountStrategy {

    @Override
    public PromotionType getSupportedType() {
        return PromotionType.PERCENT;
    }

    @Override
    public DiscountCalculationResult calculate(double unitPrice, int quantity, String discountValue) {
        try {
            double percent = Double.parseDouble(discountValue);
            if (percent < 0 || percent > 100) {
                throw new AppException(ErrorCode.INVALID_PROMOTION_VALUE);
            }
            double itemTotal = Math.max(0, (unitPrice * quantity) * (1 - (percent / 100.0)));
            double unitPriceAfterDiscount = quantity > 0 ? itemTotal / quantity : 0;
            return new DiscountCalculationResult(itemTotal, unitPriceAfterDiscount);
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.INVALID_PROMOTION_VALUE);
        }
    }
}
