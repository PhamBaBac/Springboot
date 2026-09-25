package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.response.OrderStatusHistoryResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;

import java.util.List;

public interface IOrderStatusHistoryService {

    void logStatusChange(Order order, OrderStatus fromStatus, OrderStatus toStatus, String reason, String metadata);

    List<OrderStatusHistoryResponse> getHistoriesByOrderId(String orderId);
}
