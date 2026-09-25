package com.bacpham.kanban_service.strategy.discount;

/**
 * Kết quả tính toán giảm giá cho một dòng sản phẩm (OrderItem).
 *
 * @param itemTotal Tổng tiền của dòng sản phẩm sau khi trừ giảm giá
 * @param unitPriceAfterDiscount Đơn giá trung bình của mỗi sản phẩm sau giảm giá
 * @param discountAmount Tổng số tiền giảm giá đã áp dụng cho dòng sản phẩm
 */
public record DiscountCalculationResult(
        double itemTotal,
        double unitPriceAfterDiscount,
        double discountAmount
) {
    public DiscountCalculationResult(double itemTotal, double unitPriceAfterDiscount) {
        this(itemTotal, unitPriceAfterDiscount, 0.0);
    }
}
