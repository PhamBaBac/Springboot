package com.bacpham.kanban_service.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IdempotencyLockResult {
    private final boolean acquired;
    private final boolean alreadyCompleted;
    private final String resultData;

    public static IdempotencyLockResult acquired() {
        return IdempotencyLockResult.builder()
                .acquired(true)
                .alreadyCompleted(false)
                .build();
    }

    public static IdempotencyLockResult alreadyCompleted(String resultData) {
        return IdempotencyLockResult.builder()
                .acquired(false)
                .alreadyCompleted(true)
                .resultData(resultData)
                .build();
    }
}
