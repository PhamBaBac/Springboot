package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.StatisticsResponse;
import com.bacpham.kanban_service.dto.response.StatisticsTopSellingLowQuantityResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.repository.OrderRepository;
import com.bacpham.kanban_service.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.*;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {
    private final StatisticsService statisticsService;
    private final OrderRepository orderRepository;
    @GetMapping
    @PreAuthorize("hasAuthority('admin:read')")
    public ApiResponse<StatisticsResponse> getStatistics() {
        StatisticsResponse statistics = statisticsService.getStatistics();
        return ApiResponse.<StatisticsResponse>builder()
                .result(statistics)
                .message("Statistics retrieved successfully")
                .build();
    }
    @GetMapping("/orderPurchase")
    @PreAuthorize("hasAuthority('admin:read')")
    public ApiResponse<?> getOrderPurchaseStatistics(
            @RequestParam(defaultValue = "monthly") String timeType) {

        List<Map<String, Object>> result = new ArrayList<>();

        List<Order> orders = orderRepository.findAll();

        Map<String, List<Order>> groupedOrders = new HashMap<>();
        for (Order order : orders) {
            String key = "";
            LocalDate createdAt = order.getCreatedAt().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

            if ("weekly".equalsIgnoreCase(timeType)) {
                WeekFields weekFields = WeekFields.ISO;
                int weekNumber = createdAt.get(weekFields.weekOfWeekBasedYear());
                key = createdAt.getYear() + "-W" + weekNumber;
            } else if ("yearly".equalsIgnoreCase(timeType)) {
                key = String.valueOf(createdAt.getYear());
            } else { // default monthly
                key = createdAt.getYear() + "-" + String.format("%02d", createdAt.getMonthValue());
            }
            groupedOrders.computeIfAbsent(key, k -> new ArrayList<>()).add(order);
        }


        // Tạo dữ liệu trả về
        for (Map.Entry<String, List<Order>> entry : groupedOrders.entrySet()) {
            String date = entry.getKey();
            List<Order> orderList = entry.getValue();

            int orderCount = orderList.size();
            double purchaseTotal = orderList.stream()
                    .mapToDouble(Order::getTotal)
                    .sum();

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("date", date);

            Map<String, Object> data = new HashMap<>();
            data.put("orders", orderCount);
            data.put("purchase", purchaseTotal);

            dataMap.put("data", data);

            result.add(dataMap);
        }

        result.sort(Comparator.comparing(m -> (String) m.get("date")));


        return ApiResponse.<List<Map<String, Object>>>builder()
                .result(result)
                .message("Order purchase statistics retrieved successfully")
                .build();
    }

    @GetMapping("/topSellingAndLowQuantity")
    @PreAuthorize("hasAuthority('admin:read')")
    public ApiResponse<?> getTopSellingAndLowQuantity() {
        return ApiResponse.<StatisticsTopSellingLowQuantityResponse>builder()
                .result(statisticsService.getTopSellingAndLowQuantity())
                .message("Top selling and low quantity statistics retrieved successfully")
                .build();
    }
}