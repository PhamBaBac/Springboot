package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.WishlistSyncRequest;
import com.bacpham.kanban_service.dto.request.WishlistToggleRequest;
import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.dto.response.WishlistToggleResponse;
import com.bacpham.kanban_service.service.IWishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wishlists")
@RequiredArgsConstructor
@Slf4j
public class WishlistController {

    private final IWishlistService wishlistService;

    @GetMapping
    public ApiResponse<List<ProductResponse>> getUserWishlist(Principal principal) {
        List<ProductResponse> products = wishlistService.getUserWishlist(principal.getName());
        return ApiResponse.<List<ProductResponse>>builder()
                .message("Lấy danh sách yêu thích thành công")
                .data(products)
                .build();
    }

    @GetMapping("/ids")
    public ApiResponse<List<String>> getUserWishlistIds(Principal principal) {
        List<String> ids = wishlistService.getUserWishlistProductIds(principal.getName());
        return ApiResponse.<List<String>>builder()
                .message("Lấy danh sách ID yêu thích thành công")
                .data(ids)
                .build();
    }

    @PostMapping("/toggle")
    public ApiResponse<WishlistToggleResponse> toggleWishlist(
            @Valid @RequestBody WishlistToggleRequest request,
            Principal principal
    ) {
        WishlistToggleResponse response = wishlistService.toggleWishlist(principal.getName(), request.getProductId());
        return ApiResponse.<WishlistToggleResponse>builder()
                .message(response.getMessage())
                .data(response)
                .build();
    }

    @PostMapping("/sync")
    public ApiResponse<List<String>> syncWishlist(
            @RequestBody WishlistSyncRequest request,
            Principal principal
    ) {
        List<String> ids = wishlistService.syncWishlist(principal.getName(), request.getProductIds());
        return ApiResponse.<List<String>>builder()
                .message("Đồng bộ danh sách yêu thích thành công")
                .data(ids)
                .build();
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> removeFromWishlist(
            @PathVariable String productId,
            Principal principal
    ) {
        wishlistService.removeFromWishlist(principal.getName(), productId);
        return ApiResponse.<Void>builder()
                .message("Xóa khỏi danh sách yêu thích thành công")
                .build();
    }

    @DeleteMapping("/clear")
    public ApiResponse<Void> clearWishlist(Principal principal) {
        wishlistService.clearWishlist(principal.getName());
        return ApiResponse.<Void>builder()
                .message("Đã xóa toàn bộ danh sách yêu thích")
                .build();
    }

    @GetMapping("/count/{productId}")
    public ApiResponse<Long> countLikes(@PathVariable String productId) {
        long count = wishlistService.countLikes(productId);
        return ApiResponse.<Long>builder()
                .message("Lấy số lượt yêu thích thành công")
                .data(count)
                .build();
    }
}
