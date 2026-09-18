package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.CartCreateRequest;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.service.IRedisCartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/redisCarts")
@RequiredArgsConstructor
@Slf4j

public class RedisCartController {

    private final IRedisCartService redisCartService;

    @PostMapping
    public ApiResponse<?> addToCart(@RequestBody CartCreateRequest request) {
        log.info("Thêm sản phẩm vào giỏ hàng Redis: {}", request.toString());

        redisCartService.addToCart(request.getCreatedBy(), request);
        return ApiResponse.builder()
                .message("Thêm vào giỏ hàng thành công")
                .build();
    }

    @GetMapping
    public ApiResponse<List<CartResponse>> getCart(@RequestParam String sessionId) {
        List<CartResponse> cart = redisCartService.getCart(sessionId);
        return ApiResponse.<List<CartResponse>>builder()
                .message("Lấy giỏ hàng thành công")
                .data(cart)
                .build();
    }

    @DeleteMapping
    public ApiResponse<?> clearCart(@RequestParam String sessionId) {
        redisCartService.clearCart(sessionId);
        return ApiResponse.builder()
                .message("Đã làm trống giỏ hàng")
                .build();
    }

    @PutMapping("/update")
    public ApiResponse<?> updateCart(
            @RequestParam String id,
            @RequestParam int count
    ) {
        redisCartService.updateCart(id, count);
        return ApiResponse.builder()
                .message("Cập nhật giỏ hàng thành công")
                .build();
    }
    @DeleteMapping("/remove")
    public ApiResponse<?> deleteOneCartItem(
            @RequestParam String sessionId,
            @RequestParam String cartId
    ) {
        redisCartService.deleteOneCartItem(sessionId, cartId);
        return ApiResponse.builder()
                .message("Xóa sản phẩm khỏi giỏ hàng thành công")
                .build();
    }

    @PutMapping("/updateFull")
    public ApiResponse<CartResponse> updateCartFull(
            @RequestParam String sessionId,
            @RequestParam String currentSubProductId,
            @RequestBody CartCreateRequest updatedRequest
    ) {
        CartResponse updatedCart = redisCartService.updateCartFull(sessionId, currentSubProductId, updatedRequest);
        return ApiResponse.<CartResponse>builder()
                .message("Cập nhật giỏ hàng thành công")
                .data(updatedCart)
                .build();
    }
    @PutMapping("/syncToDatabase")
    public ApiResponse<?> syncToDatabase(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestParam String userId
    ) {
        redisCartService.syncToDatabase(sessionId, userId);
        return ApiResponse.builder()
                .message("Đồng bộ giỏ hàng thành công")
                .build();
    }
}

