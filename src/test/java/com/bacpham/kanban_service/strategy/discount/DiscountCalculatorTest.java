package com.bacpham.kanban_service.strategy.discount;

import com.bacpham.kanban_service.dto.request.DiscountRequest;
import com.bacpham.kanban_service.enums.PromotionType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiscountCalculatorTest {

    private DiscountCalculator discountCalculator;

    @BeforeEach
    void setUp() {
        FixedAmountDiscountStrategy fixedAmountStrategy = new FixedAmountDiscountStrategy();
        PercentageDiscountStrategy percentageStrategy = new PercentageDiscountStrategy();
        discountCalculator = new DiscountCalculator(List.of(fixedAmountStrategy, percentageStrategy));
    }

    @Test
    @DisplayName("Không có mã giảm giá -> Trả về nguyên giá gốc")
    void testNoDiscount() {
        DiscountCalculationResult result = discountCalculator.calculate(100_000, 2, null);
        assertEquals(200_000, result.itemTotal());
        assertEquals(100_000, result.unitPriceAfterDiscount());
        assertEquals(0, result.discountAmount());
    }

    @Test
    @DisplayName("Giảm giá tiền cố định (DISCOUNT) -> Trừ đúng số tiền và tính lại đơn giá")
    void testFixedAmountDiscount() {
        DiscountRequest request = DiscountRequest.builder()
                .type(PromotionType.DISCOUNT)
                .value("30000")
                .build();

        // Đơn giá 50.000, mua 2 chiếc = 100.000, giảm 30.000 -> còn 70.000 (đơn vị 35.000/chiếc)
        DiscountCalculationResult result = discountCalculator.calculate(50_000, 2, request);
        assertEquals(70_000, result.itemTotal());
        assertEquals(35_000, result.unitPriceAfterDiscount());
        assertEquals(30_000, result.discountAmount());
    }

    @Test
    @DisplayName("Giảm giá vượt quá giá trị đơn hàng -> Không được âm (về 0)")
    void testFixedAmountExceedsTotal() {
        DiscountRequest request = DiscountRequest.builder()
                .type(PromotionType.DISCOUNT)
                .value("150000")
                .build();

        DiscountCalculationResult result = discountCalculator.calculate(50_000, 2, request);
        assertEquals(0, result.itemTotal());
        assertEquals(0, result.unitPriceAfterDiscount());
        assertEquals(100_000, result.discountAmount());
    }

    @Test
    @DisplayName("Giảm giá phần trăm (PERCENT) -> Giảm đúng tỷ lệ %")
    void testPercentageDiscount() {
        DiscountRequest request = DiscountRequest.builder()
                .type(PromotionType.PERCENT)
                .value("20")
                .build();

        // 100.000 * 3 = 300.000, giảm 20% = 240.000 (đơn vị 80.000/chiếc)
        DiscountCalculationResult result = discountCalculator.calculate(100_000, 3, request);
        assertEquals(240_000, result.itemTotal());
        assertEquals(80_000, result.unitPriceAfterDiscount());
        assertEquals(60_000, result.discountAmount());
    }

    @Test
    @DisplayName("Giá trị giảm giá không hợp lệ -> Ném AppException INVALID_PROMOTION_VALUE")
    void testInvalidValueThrowsException() {
        DiscountRequest request = DiscountRequest.builder()
                .type(PromotionType.PERCENT)
                .value("invalid_number")
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                discountCalculator.calculate(100_000, 1, request));
        assertEquals(ErrorCode.INVALID_PROMOTION_VALUE, ex.getErrorCode());
    }
}
