package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.Mockito.*;

class OrderStatusHandlerRegistryTest {

    private OrderStatusHandlerRegistry registry;
    private OrderStatusHandler processingHandler;
    private OrderStatusHandler cancelledHandler;

    @BeforeEach
    void setUp() {
        processingHandler = Mockito.mock(OrderStatusHandler.class);
        when(processingHandler.getTargetStatus()).thenReturn(OrderStatus.PROCESSING);

        cancelledHandler = Mockito.mock(OrderStatusHandler.class);
        when(cancelledHandler.getTargetStatus()).thenReturn(OrderStatus.CANCELLED);

        registry = new OrderStatusHandlerRegistry(List.of(processingHandler, cancelledHandler));
    }

    @Test
    @DisplayName("Chuyển trạng thái PROCESSING -> Gọi đúng ProcessingHandler")
    void testDispatchToProcessingHandler() {
        Order order = new Order();
        UpdateStatusOrder request = new UpdateStatusOrder();

        registry.executeTransition(order, OrderStatus.PROCESSING, request);

        verify(processingHandler, times(1)).handle(order, request);
        verify(cancelledHandler, never()).handle(any(), any());
    }

    @Test
    @DisplayName("Chuyển trạng thái CANCELLED -> Gọi đúng CancelledHandler")
    void testDispatchToCancelledHandler() {
        Order order = new Order();
        UpdateStatusOrder request = new UpdateStatusOrder();

        registry.executeTransition(order, OrderStatus.CANCELLED, request);

        verify(cancelledHandler, times(1)).handle(order, request);
        verify(processingHandler, never()).handle(any(), any());
    }

    @Test
    @DisplayName("Chuyển sang trạng thái không có handler riêng (vd: COMPLETED) -> Không ném lỗi")
    void testDispatchWithoutHandlerDoesNotThrow() {
        Order order = new Order();
        UpdateStatusOrder request = new UpdateStatusOrder();

        registry.executeTransition(order, OrderStatus.COMPLETED, request);

        verify(processingHandler, never()).handle(any(), any());
        verify(cancelledHandler, never()).handle(any(), any());
    }
}
