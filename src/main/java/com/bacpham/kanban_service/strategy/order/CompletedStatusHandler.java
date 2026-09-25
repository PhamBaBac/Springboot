package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xử lý khi đơn hàng chuyển sang COMPLETED (Đã giao hàng thành công):
 * Cập nhật shippingStatus thành "delivered" và ghi nhận log hoàn tất.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompletedStatusHandler implements OrderStatusHandler {

    @Override
    public OrderStatus getTargetStatus() {
        return OrderStatus.COMPLETED;
    }

    @Override
    public void handle(Order order, UpdateStatusOrder request) {
        if (order.getShippingStatus() == null || !"delivered".equalsIgnoreCase(order.getShippingStatus())) {
            order.setShippingStatus("delivered");
        }
        log.info("Handled order completion for orderId: {}, trackingCode: {}", order.getId(), order.getTrackingCode());
    }
}
