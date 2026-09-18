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
 * Component chuyên trách việc hoàn trả tồn kho (Restock) an toàn dưới khóa Pessimistic Lock.
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
                .filter(item -> item.getSubProduct() != null && item.getSubProduct().getId() != null)
                .sorted(Comparator.comparing(item -> item.getSubProduct().getId()))
                .toList();

        for (OrderItem item : sortedItems) {
            SubProduct subProduct = subProductRepository.findByIdWithLock(item.getSubProduct().getId())
                    .orElse(item.getSubProduct());
            int currentStock = subProduct.getStock() != null ? subProduct.getStock() : 0;
            int currentQty = subProduct.getQty() != null ? subProduct.getQty() : 0;
            subProduct.setStock(currentStock + item.getQuantity());
            subProduct.setQty(currentQty + item.getQuantity());
            subProductRepository.save(subProduct);
            log.info("Restocked subProduct {}: +{} (new stock={})",
                    subProduct.getId(), item.getQuantity(), subProduct.getStock());
        }
    }
}
