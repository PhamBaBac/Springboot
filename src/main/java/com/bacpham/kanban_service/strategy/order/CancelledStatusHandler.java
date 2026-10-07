package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.service.IPaymentTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xử lý khi đơn hàng chuyển sang CANCELLED:
 * Ghi nhận lý do hủy đơn, hoàn trả số lượng sản phẩm vào kho,
 * và cập nhật các bút toán PENDING sang FAILED vì không thu được tiền.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelledStatusHandler implements OrderStatusHandler {

    private final InventoryRestocker inventoryRestocker;
    private final com.bacpham.kanban_service.service.IPromotionService promotionService;
    private final IPaymentTransactionService paymentTransactionService;
    private final com.bacpham.kanban_service.repository.ShipmentRepository shipmentRepository;

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
        order.setShippingStatus("cancel");

        if (order.getShipments() != null) {
            for (com.bacpham.kanban_service.entity.Shipment s : order.getShipments()) {
                s.setShippingStatus("cancel");
            }
        }
        try {
            var shipments = shipmentRepository.findByOrderId(order.getId());
            for (var s : shipments) {
                s.setShippingStatus("cancel");
                shipmentRepository.save(s);
            }
        } catch (Exception ex) {
            log.warn("Không thể đồng bộ Shipment sang cancel cho order {}: {}", order.getId(), ex.getMessage());
        }

        inventoryRestocker.restockOrderItems(order);

        try {
            promotionService.rollbackPromotionUsage(order.getId());
        } catch (Exception ex) {
            log.error("Lỗi khi hoàn trả mã khuyến mãi cho đơn hàng hủy {}: {}", order.getId(), ex.getMessage());
        }

        // Cập nhật bút toán PENDING sang FAILED vì đơn hàng đã bị hủy
        paymentTransactionService.failPendingPayment(order, reason);

        log.info("Handled order cancellation and marked pending payment as FAILED for orderId: {}, reason: {}",
                order.getId(), reason);
    }
}
