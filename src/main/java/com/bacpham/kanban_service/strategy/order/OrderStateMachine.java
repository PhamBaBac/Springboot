package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order State Machine (Quản lý vòng đời trạng thái đơn hàng).
 * Đảm bảo các chuyển đổi trạng thái (State Transitions) diễn ra theo đúng quy tắc nghiệp vụ,
 * ngăn chặn nhảy cóc trạng thái, kiểm soát điều kiện bảo vệ (Guard Conditions),
 * và tự động thực thi các side-effects qua OrderStatusHandlerRegistry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStateMachine {

    private final OrderStatusHandlerRegistry statusHandlerRegistry;

    // Transition Matrix: Định nghĩa các trạng thái kế tiếp được phép từ trạng thái hiện tại
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        // PENDING -> PROCESSING (xác nhận/chuẩn bị đơn) hoặc CANCELLED (hủy đơn)
        VALID_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));

        // PROCESSING -> COMPLETED (giao thành công) hoặc CANCELLED (hủy bởi shop/shipper)
        VALID_TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));

        // COMPLETED -> REFUNDED (hoàn tiền/đổi trả sau khi đã giao hàng)
        VALID_TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.of(OrderStatus.REFUNDED));

        // CANCELLED & REFUNDED là Terminal States (trạng thái kết thúc, không được chuyển đi đâu)
        VALID_TRANSITIONS.put(OrderStatus.CANCELLED, Collections.emptySet());
        VALID_TRANSITIONS.put(OrderStatus.REFUNDED, Collections.emptySet());
    }

    /**
     * Kiểm tra xem việc chuyển từ 'from' sang 'to' có hợp lệ trong ma trận chuyển trạng thái hay không.
     */
    public boolean isValidTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true; // Idempotent transition
        }
        Set<OrderStatus> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Lấy danh sách các trạng thái hợp lệ tiếp theo có thể chuyển đến.
     */
    public Set<OrderStatus> getNextValidStatuses(OrderStatus currentStatus) {
        if (currentStatus == null) {
            return Collections.emptySet();
        }
        return VALID_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
    }

    /**
     * Guard condition: Khách hàng chỉ được tự hủy đơn khi đơn ở PENDING và chưa xuất mã vận đơn/đóng gói kiện hàng.
     */
    public boolean canCustomerCancel(Order order) {
        if (order == null) {
            return false;
        }
        if (order.getOrderStatus() != OrderStatus.PENDING) {
            return false;
        }
        // Nếu đã có tracking code hoặc shipment đã tạo thì khách hàng không thể tự hủy
        if (order.getTrackingCode() != null && !order.getTrackingCode().isBlank()) {
            return false;
        }
        return order.getShipments() == null || order.getShipments().isEmpty();
    }

    /**
     * Thực hiện chuyển đổi trạng thái đơn hàng một cách an toàn và nhất quán.
     *
     * @param order Đơn hàng cần chuyển trạng thái
     * @param targetStatus Trạng thái mới
     * @param request Dữ liệu kèm theo (lý do hủy, mã vận đơn, v.v.)
     */
    public void transition(Order order, OrderStatus targetStatus, UpdateStatusOrder request) {
        if (order == null) {
            throw new AppException(ErrorCode.BILL_NOT_FOUND);
        }
        if (targetStatus == null) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        OrderStatus currentStatus = order.getOrderStatus();

        // 1. Kiểm tra tính Idempotent: Nếu trạng thái không đổi, không làm gì thêm
        if (currentStatus == targetStatus) {
            log.info("Order {} is already in status {}, skipping transition.", order.getId(), targetStatus);
            return;
        }

        // 2. Kiểm tra tính hợp lệ của State Transition
        if (!isValidTransition(currentStatus, targetStatus)) {
            log.warn("Invalid order status transition from {} to {} for orderId: {}", currentStatus, targetStatus, order.getId());
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }

        log.info("Transitioning order {} from {} to {}", order.getId(), currentStatus, targetStatus);

        // 3. Thực thi Handler (Strategy Pattern) để kích hoạt các nghiệp vụ liên quan (tồn kho, GHN, lý do hủy)
        statusHandlerRegistry.executeTransition(order, targetStatus, request);

        // 4. Cập nhật trạng thái mới cho Order
        order.setOrderStatus(targetStatus);
    }
}
