package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.CartCreateRequest;
import com.bacpham.kanban_service.dto.request.CartUpdateRequest;
import com.bacpham.kanban_service.dto.response.CartResponse;
import com.bacpham.kanban_service.entity.Cart;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.CartMapper;
import com.bacpham.kanban_service.repository.CartRepository;
import com.bacpham.kanban_service.repository.SubProductRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.ICartService;
import com.bacpham.kanban_service.service.ISubProductService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CartServiceImpl implements ICartService {

    private final CartRepository cartRepository;
    private final CartMapper cartMapper;
    private final UserRepository userRepository;
    private final SubProductRepository subProductRepository;
    private final ISubProductService subProductService;

    @Override
    public CartResponse addToCart(CartCreateRequest request) {
        log.info("cart create request: {}", request);

        SubProduct subProduct = subProductService.findById(request.getSubProductId());
        if (subProduct == null) {
            throw new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND);
        }

        int availableStock = subProduct.getAvailableStock();
        int requestedCount = request.getCount();

        if (requestedCount > availableStock) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
        }

        Cart cart = cartMapper.toEntity(request);
        Cart saved = cartRepository.save(cart);

        return cartMapper.toResponse(saved);
    }



    @Override
    public CartResponse updateCart(String cartId, int count) {
        return updateCart(cartId, count, null);
    }

    @Override
    public CartResponse updateCart(String cartId, int count, String userId) {
        log.info("cart update request: {}", cartId);
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        if (userId != null && cart.getCreatedBy() != null && !userId.equals(cart.getCreatedBy().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (count > (cart.getSubProduct() != null ? cart.getSubProduct().getAvailableStock() : 0)) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
        }
        cart.setCount(count);
        return cartMapper.toResponse(cartRepository.save(cart));
    }

    @Override
    public void deleteCart(String cartId) {
        deleteCart(cartId, null);
    }

    @Override
    public void deleteCart(String cartId, String userId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        if (userId != null && cart.getCreatedBy() != null && !userId.equals(cart.getCreatedBy().getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        cartRepository.delete(cart);
    }
    @Override
    public List<CartResponse> getUserCart(String userName) {
        User user = userRepository.findByEmail(userName)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return cartRepository.findByCreatedBy(user).stream()
                .map(cart -> {
                    CartResponse response = cartMapper.toResponse(cart);
                    SubProduct subProduct = cart.getSubProduct();
                    boolean isDel = false;
                    int currentStock = 0;
                    if (subProduct == null || Boolean.TRUE.equals(subProduct.getDeleted())
                            || subProduct.getProduct() == null || Boolean.TRUE.equals(subProduct.getProduct().getDeleted())) {
                        isDel = true;
                        currentStock = 0;
                    } else {
                        currentStock = subProduct.getAvailableStock();
                    }
                    response.setIsDeleted(isDel);
                    response.setStock(currentStock);
                    response.setQty(currentStock);
                    return response;
                })
                .collect(Collectors.toList());
    }
    @Override
    public Optional<Cart> findByUserIdAndSubProductId(String userId, String subProductId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        SubProduct subProduct = subProductRepository.findById(subProductId)
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));
        return cartRepository.findByCreatedByAndSubProduct(user, subProduct);
    }
    @Override
    public void updateCartQuantity(String id, int countToAddOrUpdate) {
        Cart cart = cartRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));
        cart.setCount(cart.getCount() + countToAddOrUpdate);
        cartRepository.save(cart);
    }
    @Override
    public CartResponse updateCartFull(CartUpdateRequest request, String id) {
        log.info("cart update full id: {} ", id);

        Cart currentCart = cartRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        SubProduct newSubProduct = subProductRepository.findById(request.getSubProductId())
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

        User user = userRepository.findById(request.getCreatedBy())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Optional<Cart> existingCartOpt = cartRepository
                .findBySubProduct_IdAndCreatedByAndIdNot(
                        request.getSubProductId(), user, id
                );


        int adjustedCount = request.getCount();
        int available = newSubProduct.getAvailableStock();
        if (request.getCount() > available) {
            adjustedCount = available;
            log.warn("Số lượng yêu cầu vượt quá tồn kho khả dụng, đã điều chỉnh còn {}", adjustedCount);
        }

        if (existingCartOpt.isPresent()) {
            Cart existingCart = existingCartOpt.get();

            int totalCount = existingCart.getCount() + adjustedCount;
            if (totalCount > available) {
                totalCount = available;
            }

            existingCart.setCount(totalCount);
            cartRepository.save(existingCart);

            cartRepository.delete(currentCart);

            return cartMapper.toResponse(existingCart);
        } else {
            currentCart.setSubProduct(newSubProduct);
            currentCart.setProductId(request.getProductId());
            currentCart.setCount(adjustedCount);
            currentCart.setCreatedBy(user);
            currentCart.setColor(request.getColor());
            currentCart.setSize(request.getSize());
            currentCart.setPrice(request.getPrice());
            currentCart.setImage(request.getImage());
            currentCart.setQty(request.getQty());

            cartRepository.save(currentCart);

            return cartMapper.toResponse(currentCart);
        }
    }



}
