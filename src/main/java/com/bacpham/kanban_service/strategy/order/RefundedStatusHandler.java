package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xử lý khi đơn hàng chuyển sang REFUNDED:
 * Ghi nhận lý do hoàn tiền và hoàn trả số lượng sản phẩm vào kho.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundedStatusHandler implements OrderStatusHandler {

    private final InventoryRestocker inventoryRestocker;

    @Override
    public OrderStatus getTargetStatus() {
        return OrderStatus.REFUNDED;
    }

    @Override
    public void handle(Order order, UpdateStatusOrder request) {
        if (request != null && request.getCancelReason() != null && !request.getCancelReason().trim().isEmpty()) {
            order.setCancelReason(request.getCancelReason().trim());
        }
        inventoryRestocker.restockOrderItems(order);
        log.info("Handled order refund for orderId: {}", order.getId());
    }
}
