package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.service.IGhnShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xử lý khi đơn hàng chuyển sang PROCESSING:
 * Tự động gọi API GHN để tạo vận đơn nếu đơn chưa có trackingCode.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingStatusHandler implements OrderStatusHandler {

    private final IGhnShippingService ghnShippingService;

    @Override
    public OrderStatus getTargetStatus() {
        return OrderStatus.PROCESSING;
    }

    @Override
    public void handle(Order order, UpdateStatusOrder request) {
        if (request != null && request.getTrackingCode() != null && !request.getTrackingCode().isBlank()) {
            order.setTrackingCode(request.getTrackingCode().trim());
            if (request.getCarrier() != null && !request.getCarrier().isBlank()) {
                order.setCarrier(request.getCarrier().trim());
            } else if (order.getCarrier() == null || order.getCarrier().isBlank()) {
                order.setCarrier("OTHER");
            }
            order.setShippingStatus("ready_to_pick");
            log.info("Đơn hàng {} chuyển sang PROCESSING với carrier: {} và mã vận đơn thủ công: {}", 
                    order.getId(), order.getCarrier(), request.getTrackingCode());
            return;
        }

        if (order.getTrackingCode() == null || order.getTrackingCode().isBlank()) {
            try {
                String ghnCode = ghnShippingService.createShippingOrder(order);
                if (ghnCode != null && !ghnCode.isBlank()) {
                    order.setTrackingCode(ghnCode);
                    order.setCarrier("GHN");
                    order.setShippingStatus("ready_to_pick");
                    log.info("Tự động tạo đơn GHN thành công cho orderId {}: trackingCode={}", order.getId(), ghnCode);
                    return;
                }
            } catch (Exception e) {
                log.warn("Không thể tự động tạo đơn qua GHN cho orderId {}: {}. Sử dụng mã vận đơn nội bộ.", order.getId(), e.getMessage());
            }

            // Fallback: Tự sinh mã vận đơn nội bộ nếu GHN không khả dụng hoặc không cấu hình
            String shortId = order.getId().length() > 8 ? order.getId().substring(0, 8).toUpperCase() : order.getId();
            String fallbackCode = "SHOP-" + shortId;
            order.setTrackingCode(fallbackCode);
            order.setCarrier("SHOP_DELIVERY");
            order.setShippingStatus("ready_to_pick");
            log.info("Đã tạo mã vận đơn nội bộ cho đơn hàng {}: {}", order.getId(), fallbackCode);
        }
    }
}
