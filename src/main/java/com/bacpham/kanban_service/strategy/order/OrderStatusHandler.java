package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;

/**
 * Strategy interface xử lý các tác vụ nghiệp vụ khi chuyển sang trạng thái mới của đơn hàng.
 * Giúp loại bỏ if-else lồng nhau trong updateOrderStatus (God Method).
 */
public interface OrderStatusHandler {

    /**
     * Trạng thái mục tiêu mà handler này xử lý (PROCESSING, CANCELLED, REFUNDED,...).
     */
    OrderStatus getTargetStatus();

    /**
     * Thực thi các side-effect khi đơn hàng chuyển sang trạng thái này.
     *
     * @param order Đơn hàng cần cập nhật
     * @param request Dữ liệu từ request cập nhật trạng thái
     */
    void handle(Order order, UpdateStatusOrder request);
}
