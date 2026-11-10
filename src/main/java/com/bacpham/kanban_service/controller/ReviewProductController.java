package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ReviewProductRequest;
import com.bacpham.kanban_service.dto.request.ReviewReplyRequest;
import com.bacpham.kanban_service.dto.request.ReviewStatusUpdateRequest;
import com.bacpham.kanban_service.dto.response.ReviewProductResponse;
import com.bacpham.kanban_service.dto.response.ReviewStatisticsResponse;
import com.bacpham.kanban_service.enums.ReviewStatus;
import com.bacpham.kanban_service.service.IReviewProductService;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public ApiResponse<List<ReviewProductResponse>> getReviewsBySubProductIds(
            @RequestParam List<String> subProductIds
    ) {
        List<ReviewProductResponse> responses = reviewProductService.getReviewsBySubProductIds(subProductIds);
        return ApiResponse.<List<ReviewProductResponse>>builder()
                .data(responses)
                .message("Lấy danh sách đánh giá thành công")
                .build();
    }

    @GetMapping("/featured")
    public ApiResponse<List<ReviewProductResponse>> getFeaturedReviews(
            @RequestParam(value = "limit", required = false, defaultValue = "8") int limit
    ) {
        List<ReviewProductResponse> responses = reviewProductService.getFeaturedReviews(limit);
        return ApiResponse.<List<ReviewProductResponse>>builder()
                .data(responses)
                .message("Lấy danh sách đánh giá tiêu biểu thành công")
                .build();
    }

    // ==========================================
    // ADMIN ENDPOINTS
    // ==========================================

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<Page<ReviewProductResponse>> getAdminReviews(
            @RequestParam(required = false) Integer star,
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) Boolean hasReply,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        String[] sortParts = sort.split(",");
        String sortProp = sortParts[0].trim();
        Sort.Direction direction = (sortParts.length > 1 && sortParts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(direction, sortProp));
        Page<ReviewProductResponse> result = reviewProductService.getAdminReviews(star, status, hasReply, keyword, pageable);

        return ApiResponse.<Page<ReviewProductResponse>>builder()
                .data(result)
                .message("Lấy danh sách đánh giá cho admin thành công")
                .build();
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<ReviewStatisticsResponse> getReviewStatistics() {
        ReviewStatisticsResponse stats = reviewProductService.getReviewStatistics();
        return ApiResponse.<ReviewStatisticsResponse>builder()
                .data(stats)
                .message("Lấy thống kê đánh giá thành công")
                .build();
    }

    @PutMapping("/admin/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<ReviewProductResponse> updateReviewStatus(
            @PathVariable String id,
            @RequestBody @Valid ReviewStatusUpdateRequest request
    ) {
        ReviewProductResponse updated = reviewProductService.updateReviewStatus(id, request.getStatus(), request.getHiddenReason());
        return ApiResponse.<ReviewProductResponse>builder()
                .data(updated)
                .message("Cập nhật trạng thái đánh giá thành công")
                .build();
    }

    @PostMapping("/admin/{id}/reply")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<ReviewProductResponse> replyToReview(
            @PathVariable String id,
            @RequestBody @Valid ReviewReplyRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        User admin = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ReviewProductResponse updated = reviewProductService.replyToReview(id, request.getReplyContent(), admin.getId());
        return ApiResponse.<ReviewProductResponse>builder()
                .data(updated)
                .message("Phản hồi đánh giá thành công")
                .build();
    }

    @PutMapping("/admin/{id}/feature")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<ReviewProductResponse> toggleFeatured(@PathVariable String id) {
        ReviewProductResponse updated = reviewProductService.toggleFeatured(id);
        return ApiResponse.<ReviewProductResponse>builder()
                .data(updated)
                .message("Cập nhật trạng thái nổi bật thành công")
                .build();
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<Void> deleteReview(@PathVariable String id) {
        reviewProductService.deleteReview(id);
        return ApiResponse.<Void>builder()
                .message("Xóa đánh giá thành công")
                .build();
    }
}
