package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.response.OrderStatusHistoryResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.OrderStatusHistory;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.repository.OrderStatusHistoryRepository;
import com.bacpham.kanban_service.service.IOrderStatusHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusHistoryServiceImpl implements IOrderStatusHistoryService {

    private final OrderStatusHistoryRepository historyRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void logStatusChange(Order order, OrderStatus fromStatus, OrderStatus toStatus, String reason, String metadata) {
        if (order == null || toStatus == null) {
            return;
        }

        String userId = "SYSTEM";
        String role = "SYSTEM";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            if (auth.getPrincipal() instanceof User userPrincipal) {
                userId = userPrincipal.getId();
            } else {
                userId = auth.getName();
            }

            role = auth.getAuthorities().stream()
                    .map(Object::toString)
                    .filter(a -> a.startsWith("ROLE_"))
                    .findFirst()
                    .map(r -> r.replace("ROLE_", ""))
                    .orElse("USER");
        }

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedById(userId)
                .changedByRole(role)
                .reason(reason)
                .metadata(metadata)
                .build();

        historyRepository.save(history);
        log.info("Logged order status history: orderId={}, {} -> {}, changedBy={}[{}], reason={}",
                order.getId(), fromStatus, toStatus, userId, role, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getHistoriesByOrderId(String orderId) {
        return historyRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::toResponse)
                .toList();
    }

    private OrderStatusHistoryResponse toResponse(OrderStatusHistory h) {
        return OrderStatusHistoryResponse.builder()
                .id(h.getId())
                .orderId(h.getOrder() != null ? h.getOrder().getId() : null)
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .changedById(h.getChangedById())
                .changedByRole(h.getChangedByRole())
                .reason(h.getReason())
                .metadata(h.getMetadata())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
