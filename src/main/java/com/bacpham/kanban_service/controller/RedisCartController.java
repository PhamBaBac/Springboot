package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.CartCreateRequest;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.service.impl.RedisCartServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/redisCarts")
@RequiredArgsConstructor
@Slf4j
public class RedisCartController {

    private final RedisCartServiceImpl redisCartService;

    @PostMapping
    public ApiResponse<?> addToCart(@RequestBody CartCreateRequest request) {
        log.info("Creating new promotion with request: {}", request.toString());

        redisCartService.addToCart(request.getCreatedBy(), request);
        return ApiResponse.builder()
                .message("Added to cart successfully")
                .build();
    }

    @GetMapping
    public ApiResponse<List<CartResponse>> getCart(@RequestParam String sessionId) {
        List<CartResponse> cart = redisCartService.getCart(sessionId);
        return ApiResponse.<List<CartResponse>>builder()
                .message("Fetched cart from Redis")
                .result(cart)
                .build();
    }

    @DeleteMapping
    public ApiResponse<?> clearCart(@RequestParam String sessionId) {
        redisCartService.clearCart(sessionId);
        return ApiResponse.builder()
                .message("Cart cleared")
                .build();
    }

    @PutMapping("/update")
    public ApiResponse<?> updateCart(
            @RequestParam String id,
            @RequestParam int count
    ) {
        redisCartService.updateCart(id, count);
        return ApiResponse.builder()
                .message("Cart updated successfully")
                .build();
    }
    @DeleteMapping("/remove")
    public ApiResponse<?> deleteOneCartItem(
            @RequestParam String sessionId,
            @RequestParam String cartId
    ) {
        redisCartService.deleteOneCartItem(sessionId, cartId);
        return ApiResponse.builder()
                .message("Deleted cart item successfully")
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
                .message("Cart updated successfully")
                .result(updatedCart)
                .build();
    }
}

