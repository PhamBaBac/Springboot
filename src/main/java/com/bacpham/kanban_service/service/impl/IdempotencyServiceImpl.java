package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.request.OrderItemRequest;
import com.bacpham.kanban_service.dto.response.IdempotencyLockResult;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.service.IIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IIdempotencyService {

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED_PREFIX = "COMPLETED:";

    private final GenericRedisService<String, String, String> redisService;

    @Override
    public IdempotencyLockResult tryAcquire(String idempotencyKey, String action, Duration inProgressTtl) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return IdempotencyLockResult.acquired();
        }

        String redisKey = buildRedisKey(action, idempotencyKey);
        Boolean isNew = redisService.setIfAbsent(redisKey, STATUS_IN_PROGRESS, inProgressTtl);

        if (Boolean.TRUE.equals(isNew)) {
            log.debug("Acquired idempotency lock for key: {}", redisKey);
            return IdempotencyLockResult.acquired();
        }

        String currentStatus = redisService.get(redisKey);
        if (currentStatus == null) {
            Boolean retryAcquire = redisService.setIfAbsent(redisKey, STATUS_IN_PROGRESS, inProgressTtl);
            if (Boolean.TRUE.equals(retryAcquire)) {
                return IdempotencyLockResult.acquired();
            }
            throw new AppException(ErrorCode.ORDER_PROCESSING_IN_PROGRESS);
        }

        if (STATUS_IN_PROGRESS.equals(currentStatus)) {
            log.warn("Duplicate concurrent request detected for key: {}", redisKey);
            throw new AppException(ErrorCode.ORDER_PROCESSING_IN_PROGRESS);
        }

        if (currentStatus.startsWith(STATUS_COMPLETED_PREFIX)) {
            String resultData = currentStatus.substring(STATUS_COMPLETED_PREFIX.length());
            log.info("Request already completed previously for key: {}, returning cached result: {}", redisKey, resultData);
            return IdempotencyLockResult.alreadyCompleted(resultData);
        }

        if ("COMPLETED".equals(currentStatus)) {
            return IdempotencyLockResult.alreadyCompleted(null);
        }

        return IdempotencyLockResult.acquired();
    }

    @Override
    public void markCompleted(String idempotencyKey, String action, String resultData, Duration completedTtl) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        String redisKey = buildRedisKey(action, idempotencyKey);
        String value = (resultData != null) ? STATUS_COMPLETED_PREFIX + resultData : "COMPLETED";
        redisService.set(redisKey, value);
        redisService.setTimeToLive(redisKey, completedTtl.toSeconds(), TimeUnit.SECONDS);
        log.debug("Marked idempotency key {} as COMPLETED with TTL {}s", redisKey, completedTtl.toSeconds());
    }

    @Override
    public void release(String idempotencyKey, String action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        String redisKey = buildRedisKey(action, idempotencyKey);
        redisService.delete(redisKey);
        log.debug("Released idempotency lock for key: {}", redisKey);
    }

    @Override
    public String generateFingerprint(String userId, String paymentType, OrderCreateRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(userId != null ? userId : "").append("|");
        sb.append(paymentType != null ? paymentType : "").append("|");

        if (request != null) {
            sb.append(request.getAddressId() != null ? request.getAddressId() : "").append("|");
            sb.append(request.getCode() != null ? request.getCode() : "").append("|");

            List<OrderItemRequest> items = request.getItems();
            if (items != null) {
                items.stream()
                        .sorted(Comparator.comparing(OrderItemRequest::getSubProductId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .forEach(item -> {
                            sb.append(item.getSubProductId()).append(":")
                                    .append(item.getCount()).append(":")
                                    .append(item.getPrice()).append(";");
                        });
            }
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(sb.toString().hashCode());
        }
    }

    private String buildRedisKey(String action, String idempotencyKey) {
        return IDEMPOTENCY_PREFIX + action + ":" + idempotencyKey.trim();
    }
}
