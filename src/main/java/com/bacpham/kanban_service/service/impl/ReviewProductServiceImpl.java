package com.bacpham.kanban_service.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bacpham.kanban_service.dto.request.ReviewProductRequest;
import com.bacpham.kanban_service.dto.response.ReviewProductResponse;
import com.bacpham.kanban_service.entity.Order;
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
        if (!reviewModerationService.isReviewApproved(request.getComment(), request.getImages())) {
            throw new AppException(ErrorCode.REVIEW_REJECTED_BY_MODERATION);
        }


        SubProduct subProduct = subProductRepository.findById(request.getSubProductId())
                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));

        User user = userRepository.findById(request.getCreatedBy())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.BILL_NOT_FOUND));

        boolean validOrder = orderRepository.existsByIdAndUserIdAndOrderStatusAndItemsSubProductId(
                request.getOrderId(),
                request.getCreatedBy(),
                OrderStatus.COMPLETED,
                request.getSubProductId()
        );
        if (!validOrder) {
            throw new AppException(ErrorCode.NO_COMPLETED_ORDER_FOR_REVIEW);
        }

        boolean alreadyReviewed = reviewRepository.existsByCreatedByIdAndSubProductIdAndOrderId(
                request.getCreatedBy(),
                request.getSubProductId(),
                request.getOrderId()
        );
        if (alreadyReviewed) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS_FOR_ORDER);
        }

        Review review = reviewProductMapper.toEntity(request);
        review.setSubProduct(subProduct);
        review.setCreatedBy(user);
        review.setOrder(order);

        Review saved = reviewRepository.save(review);

        String prodTitle = (subProduct.getProduct() != null) ? subProduct.getProduct().getTitle() : "Sản phẩm";
        String customerName = user.getFirstname() != null ? (user.getFirstname() + (user.getLastname() != null ? " " + user.getLastname() : "")) : "Khách hàng";
        eventPublisher.publishEvent(NotificationEvent.of(
                this,
                NotificationType.NEW_REVIEW,
                "Đánh giá mới từ khách hàng",
                String.format("%s đã gửi đánh giá (%d⭐) cho '%s'", customerName, request.getStar(), prodTitle),
                NotificationPriority.NORMAL,
                "/inventory",
                saved.getId()
        ));
    }


    @Override
    public List<ReviewProductResponse> getReviewsBySubProductIds(List<String> subProductIds) {
        if (subProductIds == null || subProductIds.isEmpty()) {
            return List.of();
        }

        List<Review> reviews = reviewRepository.findBySubProductIdIn(subProductIds);

        return reviews.stream()
                .map(reviewProductMapper::toResponse)
                .toList();
    }

}
