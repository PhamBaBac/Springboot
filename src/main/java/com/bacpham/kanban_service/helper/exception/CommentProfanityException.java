package com.bacpham.kanban_service.helper.exception;

import lombok.Getter;

@Getter
public class CommentProfanityException extends AppException {
    private final String violatedWord;

    public CommentProfanityException(String violatedWord) {
        super(
                ErrorCode.REVIEW_REJECTED_BY_MODERATION,
                (violatedWord != null && !violatedWord.isBlank())
                        ? "Nội dung bình luận chứa từ ngữ không phù hợp (" + violatedWord + "). Vui lòng điều chỉnh lại!"
                        : "Nội dung đánh giá bị từ chối do vi phạm tiêu chuẩn cộng đồng."
        );
        this.violatedWord = violatedWord;
    }
}
