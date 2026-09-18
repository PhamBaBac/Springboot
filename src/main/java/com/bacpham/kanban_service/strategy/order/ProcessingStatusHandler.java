package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.service.IGhnShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xử lý khi đơn hàng chuyển sang PROCESSING:
 * Tự động gọi API GHN để tạo vận đơn nếu đơn chưa có trackingCode.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingStatusHandler implements OrderStatusHandler {

    private final IGhnShippingService ghnShippingService;

    @Override
    public OrderStatus getTargetStatus() {
        return OrderStatus.PROCESSING;
    }

    @Override
    public void handle(Order order, UpdateStatusOrder request) {
        if (order.getTrackingCode() == null || order.getTrackingCode().isBlank()) {
            try {
                String ghnCode = ghnShippingService.createShippingOrder(order);
                if (ghnCode != null && !ghnCode.isBlank()) {
                    order.setTrackingCode(ghnCode);
                    order.setShippingStatus("ready_to_pick");
                    log.info("Tự động tạo đơn GHN thành công cho orderId {}: trackingCode={}", order.getId(), ghnCode);
                }
            } catch (Exception e) {
                log.error("Không thể tự động tạo đơn qua GHN cho orderId {}: {}", order.getId(), e.getMessage());
                throw new RuntimeException("Tự động tạo đơn GHN thất bại: " + e.getMessage(), e);
            }
        }
    }
}
