package com.bacpham.kanban_service.gemini.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ReviewProductRequest;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.gemini.service.ModerationCommentService;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.service.IReviewProductService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/comments")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class ModerationCommentController {
    ModerationCommentService moderationCommentService;
    IReviewProductService reviewProductService;

    @PostMapping
    public ApiResponse<String> createComment(
            @RequestBody ReviewProductRequest request
    ) {
        boolean isSafe = moderationCommentService.isCommentSafe(request.getComment(), request.getImages());

        if (isSafe) {
            reviewProductService.createReview(request);
            return ApiResponse.<String>builder()
                    .data("Bình luận của bạn đã được ghi nhận.")
                    .message("Bình luận hợp lệ và đã được phê duyệt")
                    .build();
        } else {
            throw new AppException(ErrorCode.REVIEW_REJECTED_BY_MODERATION);
        }
    }
}
