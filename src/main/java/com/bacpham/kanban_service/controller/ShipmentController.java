package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.CalculateShippingFeeRequest;
import com.bacpham.kanban_service.dto.request.CreateShipmentRequest;
import com.bacpham.kanban_service.dto.response.ShipmentResponse;
import com.bacpham.kanban_service.service.IShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Slf4j
public class ShipmentController {

    private final IShipmentService shipmentService;

    /**
     * Kê khai cân nặng, kích thước và tạo vận đơn bắn sang GHN
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/create")
    public ApiResponse<ShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        ShipmentResponse response = shipmentService.createShipment(request);
        return ApiResponse.<ShipmentResponse>builder()
                .data(response)
                .message("Tạo vận đơn và xuất kho thành công!")
                .build();
    }

    /**
     * Phân trang và tìm kiếm danh sách kiện hàng cho trang quản lý vận đơn
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/page")
    public ApiResponse<com.bacpham.kanban_service.dto.response.PageResponse<ShipmentResponse>> getShipmentsPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        var res = shipmentService.getShipmentsPage(page, pageSize, status, search);
        return ApiResponse.<com.bacpham.kanban_service.dto.response.PageResponse<ShipmentResponse>>builder()
                .data(res)
                .build();
    }

    /**
     * Lấy danh sách các kiện hàng thuộc về một đơn hàng
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/order/{orderId}")
    public ApiResponse<List<ShipmentResponse>> getShipmentsByOrderId(@PathVariable String orderId) {
        List<ShipmentResponse> response = shipmentService.getShipmentsByOrderId(orderId);
        return ApiResponse.<List<ShipmentResponse>>builder()
                .data(response)
                .build();
    }

    /**
     * Lấy thông tin chi tiết một kiện hàng
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/{id}")
    public ApiResponse<ShipmentResponse> getShipmentById(@PathVariable String id) {
        ShipmentResponse response = shipmentService.getShipmentById(id);
        return ApiResponse.<ShipmentResponse>builder()
                .data(response)
                .build();
    }

    /**
     * Tính cước phí giao hàng dự kiến từ GHN khi thay đổi cân nặng/kích thước
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping("/calculate-fee")
    public ApiResponse<Double> calculateFee(@Valid @RequestBody CalculateShippingFeeRequest request) {
        Double fee = shipmentService.calculateShippingFee(request);
        return ApiResponse.<Double>builder()
                .data(fee)
                .build();
    }
}
