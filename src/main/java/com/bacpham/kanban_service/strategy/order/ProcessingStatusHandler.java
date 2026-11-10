package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xử lý khi đơn hàng chuyển sang PROCESSING:
 * - Nếu có trackingCode trong request: cập nhật trackingCode, carrier và shippingStatus.
 * - Nếu không có trackingCode: đơn hàng chuyển sang PROCESSING (Đang chuẩn bị hàng) mà chưa tạo vận đơn,
 *   vận đơn sẽ được tạo khi Admin chủ động bấm "Đóng gói & Tạo vận đơn GHN" hoặc gán mã sau.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingStatusHandler implements OrderStatusHandler {

    @Override
    public OrderStatus getTargetStatus() {
        return OrderStatus.PROCESSING;
    }

    @Override
    public void handle(Order order, UpdateStatusOrder request) {
        if (request != null && request.getTrackingCode() != null && !request.getTrackingCode().isBlank()) {
            order.setTrackingCode(request.getTrackingCode().trim());
            if (request.getCarrier() != null && !request.getCarrier().isBlank()) {
                order.setCarrier(request.getCarrier().trim());
            } else if (order.getCarrier() == null || order.getCarrier().isBlank()) {
                order.setCarrier("OTHER");
            }
            order.setShippingStatus("ready_to_pick");
            log.info("Đơn hàng {} chuyển sang PROCESSING với carrier: {} và mã vận đơn: {}", 
                    order.getId(), order.getCarrier(), request.getTrackingCode());
            return;
        }

        log.info("Đơn hàng {} chuyển sang PROCESSING (Đang chuẩn bị hàng, chưa tạo vận đơn)", order.getId());
    }
}
