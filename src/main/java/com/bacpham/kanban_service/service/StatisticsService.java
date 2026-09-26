package com.bacpham.kanban_service.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.*;

import org.springframework.stereotype.Service;

import com.bacpham.kanban_service.dto.response.LowQuantityProductResponse;
import com.bacpham.kanban_service.dto.response.StatisticsOrderResponse;
import com.bacpham.kanban_service.dto.response.StatisticsResponse;
import com.bacpham.kanban_service.dto.response.StatisticsTopSellingLowQuantityResponse;
import com.bacpham.kanban_service.dto.response.SubProductSellingInfo;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.OrderItem;
import com.bacpham.kanban_service.mapper.StatisticsMapper;
import com.bacpham.kanban_service.repository.OrderItemRepository;
import com.bacpham.kanban_service.repository.OrderRepository;
import com.bacpham.kanban_service.repository.ProductRepository;
import com.bacpham.kanban_service.repository.SubProductRepository;
import com.bacpham.kanban_service.repository.SupplierRepository;

import com.bacpham.kanban_service.enums.OrderStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatisticsService implements IStatisticsService {
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SubProductRepository subProductRepository;
    private final StatisticsMapper statisticsMapper;

    @Override
    public StatisticsResponse getStatistics() {
        long supplierCount = supplierRepository.count();
        long productCount = productRepository.count();
        long orderCount = orderRepository.countByDeletedFalse();

        long subProductWithStockCount = subProductRepository.countSubProductsWithStock();
        long totalSubProductQty = subProductRepository.getTotalQty();
        double totalSubProductAmount = subProductRepository.getTotalSubProductAmount();

        double totalOrderAmount = orderRepository.sumTotalByStatusAndDeletedFalse(OrderStatus.COMPLETED);

        List<OrderItem> recentItems = orderItemRepository.findRecentOrderItems(20);
        List<StatisticsOrderResponse> recentSales = recentItems.stream()
                .map(statisticsMapper::toStatisticsOrderResponse)
                .toList();

        return StatisticsResponse.builder()
                .sales(recentSales)
                .suppliers(supplierCount)
                .products(productCount)
                .orders(orderCount)
                .totalOrder(totalOrderAmount)
                .subProduct(subProductWithStockCount)
                .totalSubProduct(totalSubProductAmount)
                .totalQty(totalSubProductQty)
                .build();
    }

    @Override
    public StatisticsTopSellingLowQuantityResponse getTopSellingAndLowQuantity() {
        List<SubProductSellingInfo> topSelling =
                orderItemRepository.findTopSellingSubProducts();

        List<LowQuantityProductResponse> lowQuantity =
                productRepository.findLowQuantityProducts();

        return StatisticsTopSellingLowQuantityResponse.builder()
                .topSelling(topSelling)
                .lowQuantity(lowQuantity)
                .build();
    }

    /**
     * Thống kê đơn hàng theo khoảng thời gian (weekly/monthly/yearly).
     * Logic được chuyển từ Controller xuống Service để tuân thủ SRP.
     */
    @Override
    public List<Map<String, Object>> getOrderPurchaseStatistics(String timeType) {
        List<Order> orders = orderRepository.findAll().stream()
                .filter(order -> !Boolean.TRUE.equals(order.getDeleted())
                        && order.getOrderStatus() == OrderStatus.COMPLETED)
                .toList();

        Map<String, List<Order>> groupedOrders = new HashMap<>();
        for (Order order : orders) {
            LocalDate createdAt = order.getCreatedAt().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

            String key;
            if ("weekly".equalsIgnoreCase(timeType)) {
                WeekFields weekFields = WeekFields.ISO;
                int weekNumber = createdAt.get(weekFields.weekOfWeekBasedYear());
                key = createdAt.getYear() + "-W" + weekNumber;
            } else if ("yearly".equalsIgnoreCase(timeType)) {
                key = String.valueOf(createdAt.getYear());
            } else {
                key = createdAt.getYear() + "-" + String.format("%02d", createdAt.getMonthValue());
            }
            groupedOrders.computeIfAbsent(key, k -> new ArrayList<>()).add(order);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Order>> entry : groupedOrders.entrySet()) {
            String date = entry.getKey();
            List<Order> orderList = entry.getValue();

            int orderCount = orderList.size();
            double purchaseTotal = orderList.stream()
                    .mapToDouble(Order::getTotal)
                    .sum();

            Map<String, Object> data = new HashMap<>();
            data.put("orders", orderCount);
            data.put("purchase", purchaseTotal);

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("date", date);
            dataMap.put("data", data);

            result.add(dataMap);
        }

        result.sort(Comparator.comparing(m -> (String) m.get("date")));
        return result;
    }
}