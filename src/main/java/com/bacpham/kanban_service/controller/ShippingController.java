package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.response.ShippingTrackingResponse;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.service.IGhnShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/shipping", "/shipping"})
@RequiredArgsConstructor
@Slf4j
public class ShippingController {

    private final IGhnShippingService ghnShippingService;

    @GetMapping("/tracking/{trackingCode}")
    public ApiResponse<ShippingTrackingResponse> getTrackingByCode(@PathVariable String trackingCode) {
        ShippingTrackingResponse tracking = ghnShippingService.getTrackingDetail(trackingCode);
        return ApiResponse.<ShippingTrackingResponse>builder()
                .data(tracking)
                .message(tracking != null ? "Tra cứu vận đơn thành công" : "Không tìm thấy thông tin vận đơn")
                .build();
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<ShippingTrackingResponse> getTrackingByOrderId(@PathVariable String orderId) {
        ShippingTrackingResponse tracking = ghnShippingService.getTrackingByOrderId(orderId);

        if (tracking == null) {
            throw new AppException(ErrorCode.BILL_NOT_FOUND);
        }

        return ApiResponse.<ShippingTrackingResponse>builder()
                .data(tracking)
                .message("Tra cứu vận đơn thành công")
                .build();
    }

    /**
     * Endpoint kiểm tra trạng thái hoạt động của Webhook GHN
     */
    @GetMapping({"", "/", "/webhook"})
    public ResponseEntity<Map<String, Object>> pingGhnWebhook() {
        return ResponseEntity.ok(Map.of("code", 200, "message", "GHN webhook endpoint is active"));
    }

    /**
     * Endpoint nhận Webhook từ Giao Hàng Nhanh (GHN) khi có thay đổi trạng thái vận chuyển
     */
    @PostMapping({"", "/", "/webhook"})
    public ResponseEntity<Map<String, Object>> handleGhnWebhook(@RequestBody(required = false) Map<String, Object> payload) {
        log.info("Incoming GHN webhook callback: {}", payload);
        try {
            if (payload != null && !payload.isEmpty()) {
                ghnShippingService.handleWebhookEvent(payload);
            }
        } catch (Exception e) {
            log.error("Error processing GHN webhook: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "Success"));
    }
}

