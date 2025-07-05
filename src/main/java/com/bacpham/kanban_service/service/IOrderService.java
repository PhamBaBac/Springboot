package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.*;
import com.bacpham.kanban_service.entity.Order;

import java.util.List;

public interface IOrderService {
    Order createOrderFromSelectedItems(String userId, String paymentType, OrderCreateRequest request);
    List<OrderResponse> getOrdersByUserId(String userId);
    PageResponse<OrderDetailResponse> getPagedAllOrders(int page, int pageSize);
    void cancelOrder(String userId, String orderId);
    OrderDetailResponse getOrderById(String userId, String orderId);
    void deleteOrder(String userId, String orderId);
}