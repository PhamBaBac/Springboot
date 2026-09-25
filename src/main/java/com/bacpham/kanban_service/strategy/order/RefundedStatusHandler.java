package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.enums.TransactionStatus;
import com.bacpham.kanban_service.enums.TransactionType;
import com.bacpham.kanban_service.service.IPaymentTransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Xử lý khi đơn hàng chuyển sang REFUNDED:
 * Ghi nhận lý do hoàn tiền, hoàn trả số lượng sản phẩm vào kho,
 * và ghi nhận giao dịch hoàn tiền vào sổ cái (Transaction Ledger).
 */
@Slf4j
@Component
public class RefundedStatusHandler implements OrderStatusHandler {

    private final InventoryRestocker inventoryRestocker;
    private final IPaymentTransactionService paymentTransactionService;

    @Autowired
    public RefundedStatusHandler(InventoryRestocker inventoryRestocker,
                                 IPaymentTransactionService paymentTransactionService) {
        this.inventoryRestocker = inventoryRestocker;
        this.paymentTransactionService = paymentTransactionService;
    }

    @Override

    public OrderStatus getTargetStatus() {
        return OrderStatus.REFUNDED;
    }

    @Override
    public void handle(Order order, UpdateStatusOrder request) {
        String reason = (request != null && request.getCancelReason() != null && !request.getCancelReason().trim().isEmpty())
                ? request.getCancelReason().trim()
                : "Hoàn tiền đơn hàng";

        order.setCancelReason(reason);
        inventoryRestocker.restockOrderItems(order);

        // Financial Ledger: Ghi nhận giao dịch hoàn tiền vào sổ cái dòng tiền
        paymentTransactionService.recordTransaction(
                order,
                "REFUND-" + order.getId() + "-" + System.currentTimeMillis(),
                null,
                order.getPaymentType(),
                TransactionType.REFUND,
                order.getTotal(),
                TransactionStatus.SUCCESS,
                null,
                reason
        );

        log.info("Handled order refund and financial ledger record for orderId: {}", order.getId());
    }
}
