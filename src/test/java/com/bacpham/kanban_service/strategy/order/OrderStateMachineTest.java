package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.Shipment;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.service.IOrderStatusHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderStateMachineTest {

    private OrderStatusHandlerRegistry handlerRegistry;
    private IOrderStatusHistoryService statusHistoryService;
    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        handlerRegistry = Mockito.mock(OrderStatusHandlerRegistry.class);
        statusHistoryService = Mockito.mock(IOrderStatusHistoryService.class);
        stateMachine = new OrderStateMachine(handlerRegistry, statusHistoryService);
    }


    @Test
    @DisplayName("Ma trận chuyển đổi hợp lệ: PENDING -> PROCESSING hoặc CANCELLED")
    void testPendingTransitions() {
        assertTrue(stateMachine.isValidTransition(OrderStatus.PENDING, OrderStatus.PROCESSING));
        assertTrue(stateMachine.isValidTransition(OrderStatus.PENDING, OrderStatus.CANCELLED));
        assertFalse(stateMachine.isValidTransition(OrderStatus.PENDING, OrderStatus.COMPLETED));
        assertFalse(stateMachine.isValidTransition(OrderStatus.PENDING, OrderStatus.REFUNDED));

        assertEquals(Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
                stateMachine.getNextValidStatuses(OrderStatus.PENDING));
    }

    @Test
    @DisplayName("Ma trận chuyển đổi hợp lệ: PROCESSING -> COMPLETED hoặc CANCELLED")
    void testProcessingTransitions() {
        assertTrue(stateMachine.isValidTransition(OrderStatus.PROCESSING, OrderStatus.COMPLETED));
        assertTrue(stateMachine.isValidTransition(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        assertFalse(stateMachine.isValidTransition(OrderStatus.PROCESSING, OrderStatus.PENDING));
        assertFalse(stateMachine.isValidTransition(OrderStatus.PROCESSING, OrderStatus.REFUNDED));
    }

    @Test
    @DisplayName("Ma trận chuyển đổi hợp lệ: COMPLETED -> REFUNDED")
    void testCompletedTransitions() {
        assertTrue(stateMachine.isValidTransition(OrderStatus.COMPLETED, OrderStatus.REFUNDED));
        assertFalse(stateMachine.isValidTransition(OrderStatus.COMPLETED, OrderStatus.PENDING));
        assertFalse(stateMachine.isValidTransition(OrderStatus.COMPLETED, OrderStatus.PROCESSING));
        assertFalse(stateMachine.isValidTransition(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("Trạng thái Terminal (CANCELLED, REFUNDED) -> Không thể chuyển sang bất kỳ trạng thái nào khác")
    void testTerminalStatesCannotTransition() {
        for (OrderStatus target : OrderStatus.values()) {
            if (target != OrderStatus.CANCELLED) {
                assertFalse(stateMachine.isValidTransition(OrderStatus.CANCELLED, target));
            }
            if (target != OrderStatus.REFUNDED) {
                assertFalse(stateMachine.isValidTransition(OrderStatus.REFUNDED, target));
            }
        }
        assertTrue(stateMachine.getNextValidStatuses(OrderStatus.CANCELLED).isEmpty());
        assertTrue(stateMachine.getNextValidStatuses(OrderStatus.REFUNDED).isEmpty());
    }

    @Test
    @DisplayName("Chuyển trạng thái hợp lệ -> Gọi handler và cập nhật status")
    void testValidTransitionExecutesHandlerAndUpdatesStatus() {
        Order order = Order.builder().orderStatus(OrderStatus.PENDING).build();
        order.setId("order-123");
        UpdateStatusOrder request = UpdateStatusOrder.builder().orderStatus(OrderStatus.PROCESSING).build();

        stateMachine.transition(order, OrderStatus.PROCESSING, request);

        assertEquals(OrderStatus.PROCESSING, order.getOrderStatus());
        verify(handlerRegistry, times(1)).executeTransition(order, OrderStatus.PROCESSING, request);
    }

    @Test
    @DisplayName("Chuyển trạng thái không hợp lệ -> Ném AppException INVALID_ORDER_STATUS_TRANSITION")
    void testInvalidTransitionThrowsException() {
        Order order = Order.builder().orderStatus(OrderStatus.PENDING).build();
        order.setId("order-123");
        UpdateStatusOrder request = UpdateStatusOrder.builder().orderStatus(OrderStatus.COMPLETED).build();

        AppException ex = assertThrows(AppException.class, () ->
                stateMachine.transition(order, OrderStatus.COMPLETED, request));

        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        verify(handlerRegistry, never()).executeTransition(any(), any(), any());
    }

    @Test
    @DisplayName("Chuyển trạng thái trùng với hiện tại (Idempotent) -> Không ném lỗi và không gọi handler lặp lại")
    void testSameStatusTransitionIsIdempotent() {
        Order order = Order.builder().orderStatus(OrderStatus.PROCESSING).build();
        UpdateStatusOrder request = UpdateStatusOrder.builder().orderStatus(OrderStatus.PROCESSING).build();

        stateMachine.transition(order, OrderStatus.PROCESSING, request);

        verify(handlerRegistry, never()).executeTransition(any(), any(), any());
    }

    @Test
    @DisplayName("Guard condition: Khách hàng chỉ được hủy khi đơn ở PENDING và chưa xuất mã vận đơn")
    void testCanCustomerCancel() {
        Order validPending = Order.builder().orderStatus(OrderStatus.PENDING).build();
        assertTrue(stateMachine.canCustomerCancel(validPending));

        Order pendingWithTracking = Order.builder()
                .orderStatus(OrderStatus.PENDING)
                .trackingCode("GHN123456")
                .build();
        assertFalse(stateMachine.canCustomerCancel(pendingWithTracking));

        Order pendingWithShipment = Order.builder()
                .orderStatus(OrderStatus.PENDING)
                .shipments(List.of(new Shipment()))
                .build();
        assertFalse(stateMachine.canCustomerCancel(pendingWithShipment));

        Order processingOrder = Order.builder().orderStatus(OrderStatus.PROCESSING).build();
        assertFalse(stateMachine.canCustomerCancel(processingOrder));

        Order completedOrder = Order.builder().orderStatus(OrderStatus.COMPLETED).build();
        assertFalse(stateMachine.canCustomerCancel(completedOrder));
    }
}
