package com.bacpham.kanban_service.strategy.discount;

import com.bacpham.kanban_service.enums.PromotionType;

/**
 * Strategy Interface định nghĩa thuật toán tính toán giảm giá.
 * Tuân thủ Open/Closed Principle (OCP): Khi có loại giảm giá mới (Coupon, Tiered, Buy1Get1),
 * chỉ cần thêm Strategy mới mà không sửa đổi logic OrderService hay VNPayService.
 */
public interface DiscountStrategy {

    /**
     * Loại khuyến mãi mà Strategy này hỗ trợ.
     */
    PromotionType getSupportedType();

    /**
     * Thực hiện tính toán giảm giá.
     *
     * @param unitPrice Đơn giá gốc của 1 sản phẩm
     * @param quantity Số lượng mua
     * @param discountValue Giá trị cấu hình giảm giá (ví dụ: "50000" hoặc "15")
     * @return DiscountCalculationResult chứa tổng tiền dòng và đơn giá sau giảm
     */
    DiscountCalculationResult calculate(double unitPrice, int quantity, String discountValue);
}
