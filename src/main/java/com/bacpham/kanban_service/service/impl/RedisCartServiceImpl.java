package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.CartCreateRequest;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.entity.Cart;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.CartMapper;
import com.bacpham.kanban_service.repository.SubProductRepository;
import com.bacpham.kanban_service.service.IRedisCartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCartServiceImpl implements IRedisCartService {

    private final GenericRedisService<String, String, CartCreateRequest> redisService;
    private final CartMapper cartMapper;
    private final CartServiceImpl cartService;
    private final SubProductRepository subProductRepository;

    private String buildKey(String sessionId) {
        return "redis_cart:" + sessionId;
    }

    @Override
    public void addToCart(String sessionId, CartCreateRequest request) {
        String key = buildKey(sessionId);
        String field = request.getSubProductId();

        // 1. Lấy số lượng tồn kho từ DB
        SubProduct subProduct = subProductRepository.findById(request.getSubProductId())
                .orElseThrow(() -> new RuntimeException("Sub-product not found"));

        int stockQty = subProduct.getQty();

        // 2. Lấy toàn bộ giỏ hàng từ Redis
        Map<String, CartCreateRequest> currentCart = redisService.getField(key);

        int currentCount = currentCart.values().stream()
                .filter(item -> item.getSubProductId().equals(request.getSubProductId()))
                .mapToInt(CartCreateRequest::getCount)
                .sum();

        int totalAfterAdd = currentCount + request.getCount();

        // 4. Kiểm tra tồn kho
        if (totalAfterAdd > stockQty) {
            throw new RuntimeException("Số lượng vượt quá tồn kho. Hiện tại còn: " + (stockQty - currentCount));
        }

        redisService.hashSet(key, field, request);
    }


    @Override
    public List<CartResponse> getCart(String sessionId) {
        String key = buildKey(sessionId);
        log.info("Fetching cart for sessionId: {}", sessionId);

        return redisService.getField(key).values().stream()
                .map(request -> cartMapper.toResponse(cartMapper.toEntity(request)))
                .collect(Collectors.toList());
    }


    @Override
    public void clearCart(String sessionId) {
        String key = buildKey(sessionId);
        redisService.delete(key);
    }
    @Override
    public void syncToDatabase(String sessionId, String userId) {
        String key = buildKey(sessionId);
        Map<String, CartCreateRequest> redisCartMap = redisService.getField(key);

        if (redisCartMap == null || redisCartMap.isEmpty()) return;

        for (CartCreateRequest request : redisCartMap.values()) {
            request.setCreatedBy(userId);

            // Lấy thông tin subProduct và tồn kho
            SubProduct subProduct = subProductRepository.findById(request.getSubProductId())
                    .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));
            int stockQty = subProduct.getQty();

            // Lấy số lượng đã có trong DB cart
            Optional<Cart> dbCartOpt = cartService.findByUserIdAndSubProductId(userId, request.getSubProductId());
            int redisQty = request.getCount();
            int dbQty = dbCartOpt.map(Cart::getCount).orElse(0);
            int combinedQty = redisQty + dbQty;

            // Nếu vượt tồn kho thì cảnh báo và giới hạn lại
            int totalQty = Math.min(combinedQty, stockQty);
            if (combinedQty > stockQty) {
                log.warn("Sản phẩm [{}] vượt tồn kho: {}. Đã reset về tồn kho tối đa: {}",
                        request.getSubProductId(), combinedQty, stockQty);
            }

            if (dbCartOpt.isPresent()) {
                Cart dbCart = dbCartOpt.get();
                dbCart.setCount(totalQty);
                cartService.updateCartQuantity(dbCart.getId(), totalQty - dbCart.getCount());
            } else {
                request.setCount(totalQty);
                cartService.addToCart(request);
            }
        }

        redisService.delete(key);
    }

    @Override
    public void updateCart(String sessionId, int count) {
        String key = buildKey(sessionId);
        Map<String, CartCreateRequest> redisCartMap = redisService.getField(key);

        if (redisCartMap == null || redisCartMap.isEmpty()) return;

        for (Map.Entry<String, CartCreateRequest> entry : redisCartMap.entrySet()) {
            CartCreateRequest request = entry.getValue();
            request.setCount(count);
            redisService.hashSet(key, entry.getKey(), request);
        }
    }
    @Override
    public void deleteOneCartItem(String sessionId, String subProductId) {
        String key = buildKey(sessionId);
        if (!redisService.hashExists(key, subProductId)) {
            throw new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND);
        }
        redisService.delete(key, subProductId);
    }
    @Override
    public CartResponse updateCartFull(String sessionId, String currentSubProductId, CartCreateRequest request) {
        String key = buildKey(sessionId);
        log.info("Redis updateCartFull for sessionId: {}, currentSubProductId: {}", sessionId, currentSubProductId);

        if (!currentSubProductId.equals(request.getSubProductId())) {
            redisService.delete(key, currentSubProductId);
        }

        SubProduct subProduct = subProductRepository.findById(request.getSubProductId())
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));
        int stockQty = subProduct.getQty();

        Map<String, CartCreateRequest> currentCart = redisService.getField(key);
        Optional<CartCreateRequest> existingOpt = Optional.ofNullable(currentCart.get(request.getSubProductId()));

        int adjustedCount = request.getCount();
        if (adjustedCount > stockQty) {
            adjustedCount = stockQty;
            log.warn("Số lượng vượt tồn kho, đã điều chỉnh: {}", adjustedCount);
        }

        if (existingOpt.isPresent()) {
            CartCreateRequest existing = existingOpt.get();
            int newCount = existing.getCount() + adjustedCount;
            if (newCount > stockQty) {
                newCount = stockQty;
            }
            existing.setCount(newCount);
            redisService.hashSet(key, request.getSubProductId(), existing);
            return cartMapper.toResponse(cartMapper.toEntity(existing));
        } else {
            request.setCount(adjustedCount);
            redisService.hashSet(key, request.getSubProductId(), request);
            return cartMapper.toResponse(cartMapper.toEntity(request));
        }
    }



}
