package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry / Context quản lý toàn bộ các OrderStatusHandler theo Strategy Pattern.
 * Tự động đăng ký các bean OrderStatusHandler thông qua Spring DI.
 */
@Slf4j
@Component
public class OrderStatusHandlerRegistry {

    private final Map<OrderStatus, OrderStatusHandler> handlers = new EnumMap<>(OrderStatus.class);

    public OrderStatusHandlerRegistry(List<OrderStatusHandler> handlerList) {
        for (OrderStatusHandler handler : handlerList) {
            handlers.put(handler.getTargetStatus(), handler);
        }
    }

    /**
     * Thực hiện chuyển đổi trạng thái bằng handler tương ứng (nếu có).
     *
     * @param order Đơn hàng cần cập nhật
     * @param targetStatus Trạng thái mới
     * @param request Dữ liệu request cập nhật
     */
    public void executeTransition(Order order, OrderStatus targetStatus, UpdateStatusOrder request) {
        OrderStatusHandler handler = handlers.get(targetStatus);
        if (handler != null) {
            log.debug("Executing status transition handler for target status: {}", targetStatus);
            handler.handle(order, request);
        }
    }
}
