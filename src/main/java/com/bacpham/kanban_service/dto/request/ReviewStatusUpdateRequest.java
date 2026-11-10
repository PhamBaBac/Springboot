package com.bacpham.kanban_service.dto.request;

import com.bacpham.kanban_service.enums.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewStatusUpdateRequest {
    @NotNull(message = "Trạng thái đánh giá không được để trống")
    ReviewStatus status;
    String hiddenReason;
}
