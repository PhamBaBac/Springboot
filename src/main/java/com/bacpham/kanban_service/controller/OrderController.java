package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.*;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.impl.OrderServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderServiceImpl oderService;
    private final UserRepository userRepository;

    @PostMapping("/create")
    public ApiResponse<?> createBill(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String paymentType,
            @RequestBody OrderCreateRequest request
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();

        oderService.createOrderFromSelectedItems(userId, paymentType, request);
       return  ApiResponse.builder()
               .message("Bill created successfully")
               .build();
    }
    @GetMapping("/listOrders")
    public ApiResponse<List<OrderResponse>> getBillById(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();
        return ApiResponse.<List<OrderResponse>>builder()
                .result(oderService.getOrdersByUserId(userId))
                .message("Bill retrieved successfully")
                .build();
    }
    @GetMapping("/all")
    public ApiResponse<?> getAllBills(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.<PageResponse<OrderDetailResponse>>builder()
                .result(oderService.getPagedAllOrders(page, pageSize))
                .message("All oders retrieved successfully")
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
                .message("Order cancelled successfully")
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
                .result(orderDetail)
                .message("Order retrieved successfully")
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
                .message("Order deleted successfully")
                .build();
    }

}
