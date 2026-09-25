package com.bacpham.kanban_service.strategy.order;

import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.OrderItem;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.repository.SubProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Component chuyên trách việc hoàn trả tồn kho (Restock) an toàn bằng Atomic SQL Update.
 * Tách biệt khỏi OrderServiceImpl để tuân thủ Single Responsibility Principle (SRP).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryRestocker {

    private final SubProductRepository subProductRepository;

    public void restockOrderItems(Order order) {
        if (order == null || order.getItems() == null) {
            return;
        }

        List<OrderItem> sortedItems = order.getItems().stream()
                .filter(item -> (item.getSubProduct() != null && item.getSubProduct().getId() != null)
                        || (item.getSkuCode() != null && !item.getSkuCode().isBlank()))
                .sorted(Comparator.comparing(item -> item.getSubProduct() != null ? item.getSubProduct().getId() : item.getSkuCode()))
                .toList();

        for (OrderItem item : sortedItems) {
            String subProductId = item.getSubProduct() != null ? item.getSubProduct().getId() : item.getSkuCode();
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            if (qty > 0 && subProductId != null) {
                int updated = subProductRepository.directRestock(subProductId, qty);
                log.info("Restocked subProduct {}: +{} via atomic SQL (rowsAffected={})",
                        subProductId, qty, updated);
            }
        }
    }
}
