package com.bacpham.kanban_service.strategy.payment;

import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.PaymentResponse;
import com.bacpham.kanban_service.enums.PaymentType;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Strategy Interface cho tất cả các cổng thanh toán (VNPay, MoMo, ZaloPay, Stripe...).
 * Tuân thủ Open/Closed Principle (OCP) và Dependency Inversion Principle (DIP):
 * Muốn bổ sung một cổng thanh toán mới chỉ cần implement interface này và đánh dấu @Component,
 * không cần sửa đổi bất kỳ controller hay service tạo order nào.
 */
public interface PaymentStrategy {

    /**
     * Định danh loại thanh toán mà Strategy này phụ trách (VNPAY, MOMO, v.v.).
     */
    PaymentType getPaymentType();

    /**
     * Tạo URL thanh toán để redirect khách hàng sang cổng tương ứng.
     * @param request Thông tin đơn hàng và sản phẩm
     * @param userId ID khách hàng
     * @param httpRequest HTTP servlet request (để lấy IP client hoặc context nếu cần)
     * @return PaymentResponse chứa paymentUrl
     */
    PaymentResponse createPaymentUrl(OrderCreateRequest request, String userId, HttpServletRequest httpRequest);

    /**
     * Xử lý và thẩm định tính hợp lệ của callback / return URL từ cổng thanh toán.
     * @param params Các tham số gửi từ cổng thanh toán (chữ ký, mã kết quả, mã giao dịch...)
     * @return PaymentCallbackResult chuẩn hóa
     */
    PaymentCallbackResult handleCallback(Map<String, String> params);
}
