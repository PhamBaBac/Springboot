package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.dto.request.ReviewProductRequest;
import com.bacpham.kanban_service.dto.response.ReviewProductResponse;
import com.bacpham.kanban_service.entity.*;
import com.bacpham.kanban_service.enums.OrderStatus;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.ReviewProductMapper;
import com.bacpham.kanban_service.repository.*;
import com.bacpham.kanban_service.service.IReviewProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Override
    public void createReview(ReviewProductRequest request) {
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

        // Kiểm tra đã review order này + subProduct này chưa
        boolean alreadyReviewed = reviewRepository.existsByCreatedByIdAndSubProductIdAndOrderId(
                request.getCreatedBy(),
                request.getSubProductId(),
                request.getOrderId()
        );
        if (alreadyReviewed) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS_FOR_ORDER);
        }

        // Tạo review
        Review review = reviewProductMapper.toEntity(request);
        review.setSubProduct(subProduct);
        review.setCreatedBy(user);
        review.setOrder(order);

        reviewRepository.save(review);
    }


//    @Override
//    public List<ReviewProductResponse> getReviewsBySubProductId(String subProductId) {
//        SubProduct subProduct = subProductRepository.findById(subProductId)
//                .orElseThrow(() -> new AppException(ErrorCode.SUB_PRODUCT_NOT_FOUND));
//
//        // Lấy các review của subProduct này
//        List<Review> reviews = reviewRepository.findBySubProductId(subProduct.getId());
//
//        // Map sang response
//        return reviews.stream()
//                .map(reviewProductMapper::toResponse)
//                .toList();
//    }

    @Override
    public List<ReviewProductResponse> getReviewsBySubProductIds(List<String> subProductIds) {
        if (subProductIds == null || subProductIds.isEmpty()) {
            return List.of();
        }

        // Lấy các review của các subProduct này
        List<Review> reviews = reviewRepository.findBySubProductIdIn(subProductIds);

        // Map sang response
        return reviews.stream()
                .map(reviewProductMapper::toResponse)
                .toList();
    }

}
