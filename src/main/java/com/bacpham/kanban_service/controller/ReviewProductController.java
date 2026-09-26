package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ReviewProductRequest;
import com.bacpham.kanban_service.dto.response.ReviewProductResponse;
import com.bacpham.kanban_service.service.IReviewProductService;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviewProducts")
@RequiredArgsConstructor
public class ReviewProductController {
    private final IReviewProductService reviewProductService;
    private final UserRepository userRepository;

    @PostMapping
    public ApiResponse<?> createReviewProduct(
            @RequestBody ReviewProductRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        request.setCreatedBy(user.getId());

        reviewProductService.createReview(request);
        return ApiResponse.builder()
                .message("Đánh giá sản phẩm thành công")
                .build();
    }

    @GetMapping("/subProducts")
    public ApiResponse<List<ReviewProductResponse> >getReviewsBySubProductIds(
            @RequestParam List<String> subProductIds
    ) {
        List<ReviewProductResponse> responses = reviewProductService.getReviewsBySubProductIds(subProductIds);
        return ApiResponse.<List<ReviewProductResponse>>builder()
                .data(responses)
                .message("Lấy danh sách đánh giá thành công")
                .build();
    }
}
