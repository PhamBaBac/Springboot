package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.dto.response.WishlistToggleResponse;

import java.util.List;

public interface IWishlistService {

    WishlistToggleResponse toggleWishlist(String userEmail, String productId);

    List<ProductResponse> getUserWishlist(String userEmail);

    List<String> getUserWishlistProductIds(String userEmail);

    List<String> syncWishlist(String userEmail, List<String> guestProductIds);

    void removeFromWishlist(String userEmail, String productId);

    void clearWishlist(String userEmail);

    long countLikes(String productId);
}
