package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.CartCreateRequest;
import com.bacpham.kanban_service.dto.request.CartUpdateRequest;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.service.ICartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {

    private final ICartService cartService;

    @PostMapping("/add")
    public ApiResponse<CartResponse> addToCart(@RequestBody CartCreateRequest request) {
        CartResponse cart = cartService.addToCart(request);
        return ApiResponse.<CartResponse>builder()
                .message("Added to cart successfully")
                .result(cart)
                .build();
    }

    @PutMapping("/update")
    public ApiResponse<CartResponse> updateCart(
            @RequestParam String id,
            @RequestParam int count
    ) {
        CartResponse cart = cartService.updateCart(id, count);
        return ApiResponse.<CartResponse>builder()
                .message("Cart updated successfully")
                .result(cart)
                .build();
    }

    @DeleteMapping("/remove")
    public ApiResponse<?> deleteCart(@RequestParam String id) {
        cartService.deleteCart(id);
        return ApiResponse.builder()
                .message("Deleted cart item successfully")
                .build();
    }

    @GetMapping
    public ApiResponse<List<CartResponse>> getUserCart(Principal connectedUser) {
        String userName = connectedUser.getName();
        List<CartResponse> cartItems = cartService.getUserCart(userName);
        return ApiResponse.<List<CartResponse>>builder()
                .message("Fetched cart successfully")
                .result(cartItems)
                .build();
    }

    @PutMapping("/updateFull")
    public ApiResponse<CartResponse> updateCartFull(@RequestBody CartUpdateRequest request,
                                                    @RequestParam String id) {
        CartResponse updatedCart = cartService.updateCartFull(request, id);
        return ApiResponse.<CartResponse>builder()
                .message("Cart updated successfully")
                .result(updatedCart)
                .build();
    }
}
