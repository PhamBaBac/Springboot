package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.response.*;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.OrderItem;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.mapper.OrderMapper;
import com.bacpham.kanban_service.mapper.ProductMapper;
import com.bacpham.kanban_service.mapper.StatisticsMapper;
import com.bacpham.kanban_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        long orderCount = orderRepository.count();

        long subProductWithStockCount = subProductRepository.countSubProductsWithStock();
        long totalSubProductQty = subProductRepository.getTotalQty();
        double totalSubProductAmount = subProductRepository.getTotalSubProductAmount();

        double totalOrderAmount = orderRepository.findAll().stream()
                .filter(order -> !order.getDeleted())
                .mapToDouble(Order::getTotal)
                .sum();

        List<OrderItem> allOrders = orderItemRepository.findAll();

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