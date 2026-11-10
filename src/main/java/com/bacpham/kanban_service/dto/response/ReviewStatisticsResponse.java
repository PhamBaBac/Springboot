package com.bacpham.kanban_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewStatisticsResponse {
    double averageRating;
    long totalReviews;
    long fiveStarCount;
    long fourStarCount;
    long threeStarCount;
    long twoStarCount;
    long oneStarCount;
    long pendingCount;
    long approvedCount;
    long hiddenCount;
    long unrepliedCount;
}
