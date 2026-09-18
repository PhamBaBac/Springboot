package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.StatisticsResponse;
import com.bacpham.kanban_service.dto.response.StatisticsTopSellingLowQuantityResponse;
import com.bacpham.kanban_service.service.IStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {

    private final IStatisticsService statisticsService;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:read')")
    public ApiResponse<StatisticsResponse> getStatistics() {
        StatisticsResponse statistics = statisticsService.getStatistics();
        return ApiResponse.<StatisticsResponse>builder()
                .data(statistics)
                .message("Lấy số liệu thống kê thành công")
                .build();
    }

    @GetMapping("/orderPurchase")
    @PreAuthorize("hasAuthority('admin:read')")
    public ApiResponse<?> getOrderPurchaseStatistics(
            @RequestParam(defaultValue = "monthly") String timeType) {

        List<Map<String, Object>> result = statisticsService.getOrderPurchaseStatistics(timeType);
        return ApiResponse.<List<Map<String, Object>>>builder()
                .data(result)
                .message("Lấy thống kê đơn hàng thành công")
                .build();
    }

    @GetMapping("/topSellingAndLowQuantity")
    @PreAuthorize("hasAuthority('admin:read')")
    public ApiResponse<?> getTopSellingAndLowQuantity() {
        return ApiResponse.<StatisticsTopSellingLowQuantityResponse>builder()
                .data(statisticsService.getTopSellingAndLowQuantity())
                .message("Lấy thống kê sản phẩm bán chạy và sắp hết hàng thành công")
                .build();
    }
}
