package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.service.IPaymentTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xử lý khi đơn hàng chuyển sang COMPLETED (Đã giao hàng thành công):
 * Cập nhật shippingStatus thành "delivered",
 * cập nhật trạng thái bút toán thanh toán COD sang SUCCESS trong sổ cái (Transaction Ledger),
 * và ghi nhận log hoàn tất.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompletedStatusHandler implements OrderStatusHandler {

    private final IPaymentTransactionService paymentTransactionService;

    @Override
    public OrderStatus getTargetStatus() {
        return OrderStatus.COMPLETED;
    }

    @Override
    public void handle(Order order, UpdateStatusOrder request) {
        if (order.getShippingStatus() == null || !"delivered".equalsIgnoreCase(order.getShippingStatus())) {
            order.setShippingStatus("delivered");
        }

        // Tự động chuyển bút toán thanh toán PENDING (đặc biệt là đơn COD) sang SUCCESS vì khách đã nhận hàng và trả tiền
        paymentTransactionService.completePendingPayment(order);

        log.info("Handled order completion and updated payment ledger to SUCCESS for orderId: {}, trackingCode: {}",
                order.getId(), order.getTrackingCode());
    }
}
