package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.CartCreateRequest;
import com.bacpham.kanban_service.dto.request.CartUpdateRequest;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.service.ICartService;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {

    private final ICartService cartService;
    private final UserRepository userRepository;

    private User getAuthenticatedUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByEmail(principal.getName()).orElse(null);
    }

    @PostMapping("/add")
    public ApiResponse<CartResponse> addToCart(
            @RequestBody CartCreateRequest request,
            Principal connectedUser
    ) {
        User user = getAuthenticatedUser(connectedUser);
        if (user != null) {
            request.setCreatedBy(user.getId());
        }
        CartResponse cart = cartService.addToCart(request);
        return ApiResponse.<CartResponse>builder()
                .message("Thêm vào giỏ hàng thành công")
                .data(cart)
                .build();
    }

    @PutMapping("/update")
    public ApiResponse<CartResponse> updateCart(
            @RequestParam String id,
            @RequestParam int count,
            Principal connectedUser
    ) {
        User user = getAuthenticatedUser(connectedUser);
        String userId = user != null ? user.getId() : null;
        CartResponse cart = cartService.updateCart(id, count, userId);
        return ApiResponse.<CartResponse>builder()
                .message("Cập nhật giỏ hàng thành công")
                .data(cart)
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> deleteCartById(
            @PathVariable String id,
            Principal connectedUser
    ) {
        User user = getAuthenticatedUser(connectedUser);
        String userId = user != null ? user.getId() : null;
        cartService.deleteCart(id, userId);
        return ApiResponse.builder()
                .message("Xóa sản phẩm khỏi giỏ hàng thành công")
                .build();
    }

    @DeleteMapping("/remove")
    public ApiResponse<?> deleteCart(
            @RequestParam String id,
            Principal connectedUser
    ) {
        User user = getAuthenticatedUser(connectedUser);
        String userId = user != null ? user.getId() : null;
        cartService.deleteCart(id, userId);
        return ApiResponse.builder()
                .message("Xóa sản phẩm khỏi giỏ hàng thành công")
                .build();
    }

    @GetMapping
    public ApiResponse<List<CartResponse>> getUserCart(Principal connectedUser) {
        String userName = connectedUser.getName();
        List<CartResponse> cartItems = cartService.getUserCart(userName);
        return ApiResponse.<List<CartResponse>>builder()
                .message("Lấy thông tin giỏ hàng thành công")
                .data(cartItems)
                .build();
    }

    @PutMapping("/updateFull")
    public ApiResponse<CartResponse> updateCartFull(@RequestBody CartUpdateRequest request,
                                                    @RequestParam String id) {
        CartResponse updatedCart = cartService.updateCartFull(request, id);
        return ApiResponse.<CartResponse>builder()
                .message("Cập nhật giỏ hàng thành công")
                .data(updatedCart)
                .build();
    }


}
