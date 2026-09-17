package com.bacpham.kanban_service.service;

import java.util.List;

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
public class StatisticsService {
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SubProductRepository subProductRepository;
    private final StatisticsMapper statisticsMapper;

    public StatisticsResponse getStatistics() {
        // Get counts
        long supplierCount = supplierRepository.count();
        long productCount = productRepository.count();

        List<Order> validOrders = orderRepository.findAll().stream()
                .filter(order -> !Boolean.TRUE.equals(order.getDeleted()))
                .toList();
        long orderCount = validOrders.size();

        long subProductWithStockCount = subProductRepository.countSubProductsWithStock();
        long totalSubProductQty = subProductRepository.getTotalQty();
        double totalSubProductAmount = subProductRepository.getTotalSubProductAmount();

        double totalOrderAmount = validOrders.stream()
                .filter(order -> order.getOrderStatus() == OrderStatus.COMPLETED)
                .mapToDouble(Order::getTotal)
                .sum();

        List<OrderItem> allOrders = orderItemRepository.findAll().stream()
                .filter(item -> item.getOrder() != null && !Boolean.TRUE.equals(item.getOrder().getDeleted()))
                .toList();

        List<StatisticsOrderResponse> recentSales = allOrders.stream()
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

    public StatisticsTopSellingLowQuantityResponse getTopSellingAndLowQuantity() {
        // 1) Lấy top 5 sản phẩm con bán chạy nhất (đã gộp từ JPQL)
        List<SubProductSellingInfo> topSelling =
                orderItemRepository.findTopSellingSubProducts();

        // 2) Lấy top 5 sản phẩm có tồn kho thấp nhất (đã gộp từ JPQL)
        List<LowQuantityProductResponse> lowQuantity =
                productRepository.findLowQuantityProducts();

        // Trả về kết quả tổng hợp
        return StatisticsTopSellingLowQuantityResponse.builder()
                .topSelling(topSelling)
                .lowQuantity(lowQuantity)
                .build();
    }

}