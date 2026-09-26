package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.request.UpdateStatusOrder;
import com.bacpham.kanban_service.dto.response.*;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.IIdempotencyService;
import com.bacpham.kanban_service.service.IOrderService;
import com.bacpham.kanban_service.service.IOrderStatusHistoryService;
import com.bacpham.kanban_service.service.IPaymentTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final IOrderService oderService;
    private final UserRepository userRepository;
    private final IIdempotencyService idempotencyService;
    private final IOrderStatusHistoryService orderStatusHistoryService;
    private final IPaymentTransactionService paymentTransactionService;


    @PostMapping("/create")
    public ApiResponse<?> createBill(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String paymentType,
            @RequestBody OrderCreateRequest request
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();

        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : idempotencyService.generateFingerprint(userId, paymentType, request);
        IdempotencyLockResult lockResult = idempotencyService.tryAcquire(effectiveKey, "order_create", Duration.ofMinutes(2));
        if (lockResult.isAlreadyCompleted()) {
            log.info("Idempotent checkout request detected for user {} with key {}. Returning existing success.", userId, effectiveKey);
            return ApiResponse.builder()
                    .message("Đơn hàng đã được tạo thành công trước đó")
                    .data(lockResult.getResultData())
                    .build();
        }

        try {
            Order order = oderService.createOrderFromSelectedItems(userId, paymentType, request);

            idempotencyService.markCompleted(effectiveKey, "order_create", order.getId(), Duration.ofHours(24));

            return ApiResponse.builder()
                    .message("Tạo đơn hàng thành công")
                    .data(order.getId())
                    .build();
        } catch (Exception e) {
            idempotencyService.release(effectiveKey, "order_create");
            throw e;
        }
    }
    @GetMapping("/listOrders")
    public ApiResponse<List<OrderResponse>> getBillById(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();
        return ApiResponse.<List<OrderResponse>>builder()
                .data(oderService.getOrdersByUserId(userId))
                .message("Lấy danh sách đơn hàng thành công")
                .build();
    }
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<?> getAllBills(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return ApiResponse.<PageResponse<OrderDetailResponse>>builder()
                .data(oderService.getPagedOrders(status, search, startDate, endDate, page, pageSize))
                .message("Lấy toàn bộ danh sách đơn hàng thành công")
                .build();
    }

    @GetMapping("/filter")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<?> filterBills(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return ApiResponse.<PageResponse<OrderDetailResponse>>builder()
                .data(oderService.getPagedOrders(status, search, startDate, endDate, page, pageSize))
                .message("Lọc danh sách đơn hàng thành công")
                .build();
    }

    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<?> cancelOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String orderId
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();

        oderService.cancelOrder(userId, orderId);
        return ApiResponse.builder()
                .message("Hủy đơn hàng thành công")
                .build();
    }

    @GetMapping("/admin/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<PaymentTransactionResponse>> getAdminTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PageResponse<PaymentTransactionResponse> pagedResult = paymentTransactionService.getPagedTransactions(page, pageSize);
        return ApiResponse.<PageResponse<PaymentTransactionResponse>>builder()
                .data(pagedResult)
                .message("Lấy danh sách giao dịch tài chính đối soát thành công")
                .build();
    }

    @GetMapping("/status-counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<Map<String, Long>> getOrderStatusCounts() {
        return ApiResponse.<Map<String, Long>>builder()
                .data(oderService.getOrderStatusCounts())
                .message("Lấy thống kê số lượng đơn hàng theo trạng thái thành công")
                .build();
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrderById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String orderId
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();

        OrderDetailResponse orderDetail = oderService.getOrderById(userId, orderId);
        return ApiResponse.<OrderDetailResponse>builder()
                .data(orderDetail)
                .message("Lấy thông tin đơn hàng thành công")
                .build();
    }

    @DeleteMapping("/{orderId}")
    public ApiResponse<?> deleteOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String orderId
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();

        oderService.deleteOrder(userId, orderId);
        return ApiResponse.builder()
                .message("Xóa đơn hàng thành công")
                .build();
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<?> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody UpdateStatusOrder status
    ) {
        log.info("Cập nhật trạng thái đơn hàng {}: {}", orderId, status);
        oderService.updateOrderStatus(orderId, status);
        return ApiResponse.builder()
                .message("Cập nhật trạng thái đơn hàng thành công")
                .build();
    }

    @GetMapping("/{orderId}/status-history")
    public ApiResponse<List<OrderStatusHistoryResponse>> getOrderStatusHistory(@PathVariable String orderId) {
        List<OrderStatusHistoryResponse> histories = orderStatusHistoryService.getHistoriesByOrderId(orderId);
        return ApiResponse.<List<OrderStatusHistoryResponse>>builder()
                .data(histories)
                .message("Lấy lịch sử trạng thái đơn hàng thành công")
                .build();
    }

    @GetMapping("/{orderId}/transactions")
    public ApiResponse<List<PaymentTransactionResponse>> getOrderTransactions(@PathVariable String orderId) {
        List<PaymentTransactionResponse> transactions = paymentTransactionService.getTransactionsByOrderId(orderId);
        return ApiResponse.<List<PaymentTransactionResponse>>builder()
                .data(transactions)
                .message("Lấy sổ cái giao dịch của đơn hàng thành công")
                .build();
    }
}


