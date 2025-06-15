package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.CartCreateRequest;
import com.bacpham.kanban_service.dto.response.CartResponse;

import java.util.List;

public interface IRedisCartService {
    void addToCart(String sessionId, CartCreateRequest request);
    List<CartResponse> getCart(String sessionId);
    void clearCart(String sessionId);
    void syncToDatabase(String sessionId, String userId);
    //update cart in redis
    void updateCart(String sessionId, int count);
    void deleteOneCartItem(String sessionId, String cartId);
    public CartResponse updateCartFull(String sessionId, String currentSubProductId, CartCreateRequest updatedRequest)
;
}
