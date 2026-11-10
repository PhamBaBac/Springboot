package com.bacpham.kanban_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewReplyRequest {
    @NotBlank(message = "Nội dung phản hồi không được để trống")
    String replyContent;
}
