package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ReviewProductRequest;
import com.bacpham.kanban_service.dto.response.ReviewProductResponse;
import com.bacpham.kanban_service.service.impl.ReviewProductServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviewProducts")
@RequiredArgsConstructor
public class ReviewProductController {
    private final ReviewProductServiceImpl reviewProductService;

    @PostMapping
    public ApiResponse<?> createReviewProduct(
            @RequestBody ReviewProductRequest request
    ) {
        reviewProductService.createReview(request);
        return ApiResponse.builder()
                .message("Review product created successfully")
                .build();
    }

//    @GetMapping("/{subProductId}")
//    public ApiResponse<?> getReviewsBySubProductId(
//            @PathVariable String subProductId
//    ) {
//        return ApiResponse.builder()
//                .result(reviewProductService.getReviewsBySubProductId(subProductId))
//                .message("Get reviews by sub product id successfully")
//                .build();
//    }

    @GetMapping("/subProducts")
    public ApiResponse<List<ReviewProductResponse> >getReviewsBySubProductIds(
            @RequestParam List<String> subProductIds
    ) {
        List<ReviewProductResponse> responses = reviewProductService.getReviewsBySubProductIds(subProductIds);
        return ApiResponse.<List<ReviewProductResponse>>builder()
                .result(   responses)
                .message("Get reviews by sub product ids successfully")
                .build();
    }
}
