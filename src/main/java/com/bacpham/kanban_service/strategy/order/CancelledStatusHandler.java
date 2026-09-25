package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xử lý khi đơn hàng chuyển sang CANCELLED:
 * Ghi nhận lý do hủy đơn và hoàn trả số lượng sản phẩm vào kho.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelledStatusHandler implements OrderStatusHandler {

    private final InventoryRestocker inventoryRestocker;

    @Override
    public OrderStatus getTargetStatus() {
        return OrderStatus.CANCELLED;
    }

    @Override
    public void handle(Order order, UpdateStatusOrder request) {
        String reason = request != null ? request.getCancelReason() : null;
        if (reason == null || reason.trim().isEmpty()) {
            reason = "Hủy bởi Quản trị viên";
        }
        order.setCancelReason(reason);
        if (order.getShippingStatus() != null && !order.getShippingStatus().isBlank()) {
            order.setShippingStatus("cancel");
        }
        inventoryRestocker.restockOrderItems(order);
        log.info("Handled order cancellation for orderId: {}, reason: {}", order.getId(), reason);
    }
}
