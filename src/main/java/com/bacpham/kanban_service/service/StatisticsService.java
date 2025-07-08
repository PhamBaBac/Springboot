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
        List<OrderItem> orderItems = orderItemRepository.findAll();
        Map<String, SubProductSellingInfo> sellingMap = new HashMap<>();

        for (OrderItem item : orderItems) {
            SubProduct sp = item.getSubProduct();
            String subProductId = sp.getId();
            String name = sp.getProduct().getTitle() + " - " + sp.getColor() + " - " + sp.getSize();

            sellingMap.compute(subProductId, (id, dto) -> {
                if (dto == null) {
                    return SubProductSellingInfo.builder()
                            .name(name)
                            .soldQuantity(item.getQuantity())
                            .remainingQuantity(sp.getStock())
                            .price(item.getPriceAtOrderTime())
                            .build();
                } else {
                    dto.setSoldQuantity(dto.getSoldQuantity() + item.getQuantity());
                    return dto;
                }
            });
        }

        List<SubProductSellingInfo> topSelling = sellingMap.values().stream()
                .sorted(Comparator.comparingInt(SubProductSellingInfo::getSoldQuantity).reversed())
                .limit(5)
                .toList();

        // 2) Low Quantity
        List<Product> products = productRepository.findAll();
        List<LowQuantityProductResponse> lowQuantity = products.stream()
                .map(product -> {
                    Integer totalStock = subProductRepository.sumStockByProductId(product.getId());
                    return LowQuantityProductResponse.builder()
                            .name(product.getTitle())
                            .remainingQuantity(totalStock == null ? 0 : totalStock)
                            .images(product.getImages())
                            .build();
                })
                .filter(response -> response.getRemainingQuantity() < 30) // chỉ sp còn dưới 10 cái
                .sorted(Comparator.comparingInt(LowQuantityProductResponse::getRemainingQuantity))
                .limit(5)
                .toList();

        return StatisticsTopSellingLowQuantityResponse.builder()
                .topSelling(topSelling)
                .lowQuantity(lowQuantity)
                .build();
    }
}