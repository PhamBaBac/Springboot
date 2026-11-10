package com.bacpham.kanban_service.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.bacpham.kanban_service.dto.response.ReviewStatisticsResponse;
import com.bacpham.kanban_service.enums.ReviewStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bacpham.kanban_service.dto.request.ReviewProductRequest;
import com.bacpham.kanban_service.dto.response.ReviewProductResponse;
import com.bacpham.kanban_service.entity.Order;
import com.bacpham.kanban_service.entity.Product;
import com.bacpham.kanban_service.entity.Review;
import com.bacpham.kanban_service.entity.SubProduct;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.gemini.service.ReviewModerationService;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.ReviewProductMapper;
import com.bacpham.kanban_service.repository.OrderRepository;
import com.bacpham.kanban_service.repository.ReviewProductRepository;
import com.bacpham.kanban_service.repository.SubProductRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.IReviewProductService;

import com.bacpham.kanban_service.enums.NotificationPriority;
import com.bacpham.kanban_service.enums.NotificationType;
import com.bacpham.kanban_service.event.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ReviewProductServiceImpl implements IReviewProductService {
    private final SubProductRepository subProductRepository;
    private final ReviewProductRepository reviewRepository;
    private final UserRepository userRepository;
    private final ReviewProductMapper reviewProductMapper;
    private final OrderRepository orderRepository;
    private final ReviewModerationService reviewModerationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void createReview(ReviewProductRequest request) {
        reviewModerationService.validateReview(request.getComment(), request.getImages());

        SubProduct subProduct = subProductRepository.findById(request.getSubProductId())
                .or(() -> subProductRepository.findBySkuAndDeletedFalse(request.getSubProductId()))
                .or(() -> subProductRepository.findBySku(request.getSubProductId()))
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

        User user = userRepository.findById(request.getCreatedBy())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        boolean validOrder = orderRepository.existsByIdAndUserIdAndOrderStatusAndItemsSubProductId(
                request.getOrderId(),
                request.getCreatedBy(),
                OrderStatus.COMPLETED,
                subProduct.getId()
        );
        if (!validOrder && request.getSubProductId() != null && !request.getSubProductId().equals(subProduct.getId())) {
            validOrder = orderRepository.existsByIdAndUserIdAndOrderStatusAndItemsSubProductId(
                    request.getOrderId(),
                    request.getCreatedBy(),
                    OrderStatus.COMPLETED,
                    request.getSubProductId()
            );
        }
        if (!validOrder) {
            throw new AppException(ErrorCode.NO_COMPLETED_ORDER_FOR_REVIEW);
        }

        boolean alreadyReviewed = reviewRepository.existsByCreatedByIdAndSubProductIdAndOrderId(
                request.getCreatedBy(),
                subProduct.getId(),
                request.getOrderId()
        );
        if (!alreadyReviewed && request.getSubProductId() != null && !request.getSubProductId().equals(subProduct.getId())) {
            alreadyReviewed = reviewRepository.existsByCreatedByIdAndSubProductIdAndOrderId(
                    request.getCreatedBy(),
                    request.getSubProductId(),
                    request.getOrderId()
            );
        }
        if (alreadyReviewed) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS_FOR_ORDER);
        }

        Review review = reviewProductMapper.toEntity(request);
        review.setSubProduct(subProduct);
        review.setCreatedBy(user);
        review.setOrder(order);
        review.setStatus(ReviewStatus.APPROVED);
        review.setIsFeatured(false);

        Review saved = reviewRepository.save(review);

        String prodTitle = (subProduct.getProduct() != null) ? subProduct.getProduct().getTitle() : "Sản phẩm";
        String customerName = user.getFirstname() != null ? (user.getFirstname() + (user.getLastname() != null ? " " + user.getLastname() : "")) : "Khách hàng";
        eventPublisher.publishEvent(NotificationEvent.of(
                this,
                NotificationType.NEW_REVIEW,
                "Đánh giá mới từ khách hàng",
                String.format("%s đã gửi đánh giá (%d sao) cho '%s'", customerName, request.getStar(), prodTitle),
                NotificationPriority.NORMAL,
                "/reviews",
                saved.getId()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewProductResponse> getReviewsBySubProductIds(List<String> subProductIds) {
        if (subProductIds == null || subProductIds.isEmpty()) {
            return List.of();
        }

        List<Review> reviews = reviewRepository.findBySubProductIdIn(subProductIds);

        return reviews.stream()
                .map(reviewProductMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewProductResponse> getFeaturedReviews(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, safeLimit);
        List<Review> reviews = reviewRepository.findFeaturedReviews(pageable);

        return reviews.stream()
                .map(reviewProductMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewProductResponse> getAdminReviews(Integer star, ReviewStatus status, Boolean hasReply, String keyword, Pageable pageable) {
        Specification<Review> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.or(cb.isFalse(root.get("deleted")), cb.isNull(root.get("deleted"))));

            if (star != null && star >= 1 && star <= 5) {
                predicates.add(cb.equal(root.get("star"), star));
            }

            if (status != null) {
                if (status == ReviewStatus.APPROVED) {
                    predicates.add(cb.or(
                            cb.equal(root.get("status"), ReviewStatus.APPROVED),
                            cb.isNull(root.get("status"))
                    ));
                } else {
                    predicates.add(cb.equal(root.get("status"), status));
                }
            }

            if (hasReply != null) {
                if (hasReply) {
                    predicates.add(cb.and(
                            cb.isNotNull(root.get("shopReply")),
                            cb.notEqual(cb.trim(root.get("shopReply")), "")
                    ));
                } else {
                    predicates.add(cb.or(
                            cb.isNull(root.get("shopReply")),
                            cb.equal(cb.trim(root.get("shopReply")), "")
                    ));
                }
            }

            if (keyword != null && !keyword.trim().isEmpty()) {
                String searchPattern = "%" + keyword.trim().toLowerCase() + "%";
                Join<Review, User> userJoin = root.join("createdBy", JoinType.LEFT);
                Join<Review, SubProduct> subProductJoin = root.join("subProduct", JoinType.LEFT);
                Join<SubProduct, Product> productJoin = subProductJoin.join("product", JoinType.LEFT);

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("comment")), searchPattern),
                        cb.like(cb.lower(userJoin.get("firstname")), searchPattern),
                        cb.like(cb.lower(userJoin.get("lastname")), searchPattern),
                        cb.like(cb.lower(userJoin.get("email")), searchPattern),
                        cb.like(cb.lower(productJoin.get("title")), searchPattern),
                        cb.like(cb.lower(subProductJoin.get("sku")), searchPattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return reviewRepository.findAll(spec, pageable)
                .map(reviewProductMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewStatisticsResponse getReviewStatistics() {
        long total = reviewRepository.countAllReviews();
        Double avgRating = reviewRepository.getAverageApprovedRating();
        long fiveStar = reviewRepository.countByStar(5);
        long fourStar = reviewRepository.countByStar(4);
        long threeStar = reviewRepository.countByStar(3);
        long twoStar = reviewRepository.countByStar(2);
        long oneStar = reviewRepository.countByStar(1);
        long approved = reviewRepository.countApprovedReviews();
        long pending = reviewRepository.countByStatus(ReviewStatus.PENDING);
        long hidden = reviewRepository.countByStatus(ReviewStatus.HIDDEN);
        long unreplied = reviewRepository.countUnreplied();

        return ReviewStatisticsResponse.builder()
                .averageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0)
                .totalReviews(total)
                .fiveStarCount(fiveStar)
                .fourStarCount(fourStar)
                .threeStarCount(threeStar)
                .twoStarCount(twoStar)
                .oneStarCount(oneStar)
                .approvedCount(approved)
                .pendingCount(pending)
                .hiddenCount(hidden)
                .unrepliedCount(unreplied)
                .build();
    }

    @Override
    public ReviewProductResponse updateReviewStatus(String reviewId, ReviewStatus status, String hiddenReason) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_FOUND));

        review.setStatus(status);
        if (status == ReviewStatus.HIDDEN) {
            review.setHiddenReason(hiddenReason);
        } else {
            review.setHiddenReason(null);
        }

        Review saved = reviewRepository.save(review);
        log.info("Admin updated review {} status to {}", reviewId, status);
        return reviewProductMapper.toResponse(saved);
    }

    @Override
    public ReviewProductResponse replyToReview(String reviewId, String replyContent, String adminUserId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_FOUND));

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        review.setShopReply(replyContent.trim());
        review.setRepliedAt(new Date());
        review.setRepliedBy(admin);

        Review saved = reviewRepository.save(review);
        log.info("Admin {} replied to review {}", admin.getEmail(), reviewId);
        return reviewProductMapper.toResponse(saved);
    }

    @Override
    public ReviewProductResponse toggleFeatured(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_FOUND));

        boolean nextVal = !(review.getIsFeatured() != null && review.getIsFeatured());
        review.setIsFeatured(nextVal);

        Review saved = reviewRepository.save(review);
        log.info("Toggled review {} isFeatured to {}", reviewId, nextVal);
        return reviewProductMapper.toResponse(saved);
    }

    @Override
    public void deleteReview(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_FOUND));

        review.setDeleted(true);
        reviewRepository.save(review);
        log.info("Soft-deleted review {}", reviewId);
    }
}
