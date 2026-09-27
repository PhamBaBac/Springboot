package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.response.ProductResponse;
import com.bacpham.kanban_service.dto.response.WishlistToggleResponse;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.entity.Wishlist;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.ProductMapper;
import com.bacpham.kanban_service.repository.ProductRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.repository.WishlistRepository;
import com.bacpham.kanban_service.service.IWishlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistServiceImpl implements IWishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public WishlistToggleResponse toggleWishlist(String userEmail, String productId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        Optional<Wishlist> existing = wishlistRepository.findByUserIdAndProductId(user.getId(), product.getId());

        if (existing.isPresent()) {
            wishlistRepository.delete(existing.get());
            log.info("User {} removed product {} from wishlist", userEmail, productId);
            return WishlistToggleResponse.builder()
                    .productId(productId)
                    .isFavorite(false)
                    .message("Đã xóa khỏi danh sách yêu thích")
                    .build();
        } else {
            Wishlist wishlist = Wishlist.builder()
                    .user(user)
                    .product(product)
                    .build();
            wishlistRepository.save(wishlist);
            log.info("User {} added product {} to wishlist", userEmail, productId);
            return WishlistToggleResponse.builder()
                    .productId(productId)
                    .isFavorite(true)
                    .message("Đã thêm vào danh sách yêu thích")
                    .build();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getUserWishlist(String userEmail) {
        List<Product> products = wishlistRepository.findProductsByUserEmail(userEmail);
        return products.stream()
                .map(productMapper::toProductResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getUserWishlistProductIds(String userEmail) {
        return wishlistRepository.findProductIdsByUserEmail(userEmail);
    }

    @Override
    @Transactional
    public List<String> syncWishlist(String userEmail, List<String> guestProductIds) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (guestProductIds != null && !guestProductIds.isEmpty()) {
            Set<String> existingIds = new HashSet<>(wishlistRepository.findProductIdsByUserId(user.getId()));

            for (String productId : guestProductIds) {
                if (productId != null && !productId.isBlank() && !existingIds.contains(productId)) {
                    Optional<Product> productOpt = productRepository.findById(productId);
                    if (productOpt.isPresent()) {
                        Wishlist wishlist = Wishlist.builder()
                                .user(user)
                                .product(productOpt.get())
                                .build();
                        wishlistRepository.save(wishlist);
                        existingIds.add(productId);
                    }
                }
            }
        }

        return wishlistRepository.findProductIdsByUserId(user.getId());
    }

    @Override
    @Transactional
    public void removeFromWishlist(String userEmail, String productId) {
        wishlistRepository.deleteByUserEmailAndProductId(userEmail, productId);
        log.info("User {} removed product {} from wishlist", userEmail, productId);
    }

    @Override
    @Transactional
    public void clearWishlist(String userEmail) {
        wishlistRepository.deleteByUserEmail(userEmail);
        log.info("User {} cleared wishlist", userEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public long countLikes(String productId) {
        return wishlistRepository.countByProductId(productId);
    }
}
