package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.response.ShippingTrackingResponse;
import com.bacpham.kanban_service.entity.Order;

import java.util.Map;

public interface IGhnShippingService {
    /**
     * Tra cứu hành trình vận đơn từ GHN theo mã vận đơn
     */
    ShippingTrackingResponse getTrackingDetail(String orderCode);

    /**
     * Tra cứu hành trình vận đơn từ GHN theo orderId trong hệ thống
     * (tự nội bộ tìm trackingCode từ DB rồi gọi GHN)
     */
    ShippingTrackingResponse getTrackingByOrderId(String orderId);

    /**
     * Tự động tạo đơn giao hàng trên GHN Open API từ đối tượng Order
     * @param order Đối tượng Order cần tạo vận đơn
     * @return Mã vận đơn (order_code) từ GHN
     */
    String createShippingOrder(Order order);

    /**
     * Tạo vận đơn trên GHN từ đối tượng Shipment (thông số đóng gói thực tế)
     * @param shipment Kiện hàng chứa cân nặng, kích thước, danh sách items
     * @return Mã vận đơn (order_code) từ GHN
     */
    String createShippingOrderFromShipment(com.bacpham.kanban_service.entity.Shipment shipment);

    /**
     * Tính cước phí giao hàng dự kiến từ GHN
     */
    Double calculateShippingFee(com.bacpham.kanban_service.dto.request.CalculateShippingFeeRequest request);

    /**
     * Xử lý webhook từ GHN gửi về khi trạng thái vận đơn thay đổi
     * @param payload Dữ liệu sự kiện từ GHN
     */
    void handleWebhookEvent(Map<String, Object> payload);
}

