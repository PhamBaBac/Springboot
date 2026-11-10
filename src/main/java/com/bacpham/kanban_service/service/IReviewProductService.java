package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.ReviewProductRequest;
import com.bacpham.kanban_service.dto.response.ReviewProductResponse;
import com.bacpham.kanban_service.dto.response.ReviewStatisticsResponse;
import com.bacpham.kanban_service.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IReviewProductService {
    void createReview(ReviewProductRequest request);
    List<ReviewProductResponse> getReviewsBySubProductIds(List<String> subProductIds);
    List<ReviewProductResponse> getFeaturedReviews(int limit);

    // Admin APIs
    Page<ReviewProductResponse> getAdminReviews(Integer star, ReviewStatus status, Boolean hasReply, String keyword, Pageable pageable);
    ReviewStatisticsResponse getReviewStatistics();
    ReviewProductResponse updateReviewStatus(String reviewId, ReviewStatus status, String hiddenReason);
    ReviewProductResponse replyToReview(String reviewId, String replyContent, String adminUserId);
    ReviewProductResponse toggleFeatured(String reviewId);
    void deleteReview(String reviewId);
}
