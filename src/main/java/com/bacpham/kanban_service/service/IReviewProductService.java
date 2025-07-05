package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.ReviewProductRequest;
import com.bacpham.kanban_service.dto.response.ReviewProductResponse;

import java.util.List;

public interface IReviewProductService {
    void createReview(ReviewProductRequest request);
//    List<ReviewProductResponse> getReviewsBySubProductId(String subProductId);
    List<ReviewProductResponse> getReviewsBySubProductIds(List<String> subProductIds);
}
