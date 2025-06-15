package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.CartCreateRequest;
import com.bacpham.kanban_service.dto.request.CartUpdateRequest;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.entity.Cart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICartService {

    CartResponse addToCart(CartCreateRequest request);

    CartResponse updateCart(String cartId, int count);

    void deleteCart(String cartId);

    List<CartResponse> getUserCart(String userId);

    Optional<Cart> findByUserIdAndSubProductId(String userId, String subProductId);

    void updateCartQuantity(String id, int countToAddOrUpdate);

    CartResponse updateCartFull(CartUpdateRequest request, String id);
}
