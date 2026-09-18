package com.bacpham.kanban_service.service;

import java.util.List;
import java.util.Optional;

import com.bacpham.kanban_service.dto.request.CartCreateRequest;
import com.bacpham.kanban_service.dto.request.CartUpdateRequest;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.entity.Cart;

public interface ICartService {

    CartResponse addToCart(CartCreateRequest request);

    CartResponse updateCart(String cartId, int count);

    CartResponse updateCart(String cartId, int count, String userId);

    void deleteCart(String cartId);

    void deleteCart(String cartId, String userId);

    List<CartResponse> getUserCart(String userId);

    Optional<Cart> findByUserIdAndSubProductId(String userId, String subProductId);

    void updateCartQuantity(String id, int countToAddOrUpdate);

    CartResponse updateCartFull(CartUpdateRequest request, String id);
}
