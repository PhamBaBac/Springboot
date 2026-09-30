package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.PromotionRequest;
import com.bacpham.kanban_service.dto.response.PromotionResponse;
import com.bacpham.kanban_service.entity.Promotion;
import com.bacpham.kanban_service.entity.PromotionUsage;
import com.bacpham.kanban_service.enums.PromotionUsageStatus;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.PromotionMapper;
import com.bacpham.kanban_service.repository.PromotionRepository;
import com.bacpham.kanban_service.repository.PromotionUsageRepository;
import com.bacpham.kanban_service.service.IPromotionService;
import com.bacpham.kanban_service.service.RedisScriptService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionServiceImpl implements IPromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final PromotionMapper promotionMapper;
    private final GenericRedisService<String, String, Long> redisService;
    private final RedisScriptService redisScriptService;

    private String getStockKey(String code) {
        return "promotion:stock:" + code;
    }

    private String getExpireKey(String code) {
        return "promotion:expire:" + code;
    }

    private void ensureRedisState(String code) {
        String stockKey = getStockKey(code);
        String expireKey = getExpireKey(code);
        if (redisService.get(stockKey) == null || redisService.get(expireKey) == null) {
            promotionRepository.findByCodeAndDeletedFalse(code).ifPresent(p -> {
                if (p.getNumOfAvailable() != null) {
                    redisService.set(stockKey, p.getNumOfAvailable().longValue());
                }
                if (p.getEndAt() != null) {
                    long endAtMillis = p.getEndAt().toInstant(ZoneOffset.UTC).toEpochMilli();
                    redisService.set(expireKey, endAtMillis);
                }
            });
        }
    }

    @Override
    public PromotionResponse createPromotion(PromotionRequest request) {
        String code = request.getCode() != null ? request.getCode().trim().toUpperCase() : "";
        if (code.isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        if (promotionRepository.existsByCodeAndDeletedFalse(code)) {
            throw new AppException(ErrorCode.PROMOTION_CODE_ALREADY_EXISTS);
        }

        request.setCode(code);
        log.info("Creating promotion with code: {}", code);

        Promotion promotion = promotionMapper.toEntity(request);
        promotion.setDeleted(false);
        Promotion saved = promotionRepository.save(promotion);

        if (promotion.getNumOfAvailable() != null) {
            redisService.set(getStockKey(promotion.getCode()), promotion.getNumOfAvailable().longValue());
        }

        if (promotion.getEndAt() != null) {
            long endAtMillis = promotion.getEndAt().toInstant(ZoneOffset.UTC).toEpochMilli();
            redisService.set(getExpireKey(promotion.getCode()), endAtMillis);
        }

        return promotionMapper.toResponse(saved);
    }

    @Override
    public PromotionResponse getPromotionByNameCode(String code) {
        String cleanCode = code != null ? code.trim().toUpperCase() : "";
        Promotion promotion = promotionRepository.findByCodeAndDeletedFalse(cleanCode)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));
        return promotionMapper.toResponse(promotion);
    }

    @Override
    public PromotionResponse updatePromotion(String id, PromotionRequest request) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));

        if (request.getCode() != null) {
            String cleanCode = request.getCode().trim().toUpperCase();
            if (!cleanCode.equalsIgnoreCase(promotion.getCode()) && promotionRepository.existsByCodeAndDeletedFalse(cleanCode)) {
                throw new AppException(ErrorCode.PROMOTION_CODE_ALREADY_EXISTS);
            }
            request.setCode(cleanCode);
        }

        promotionMapper.updatePromotionFromRequest(request, promotion);
        Promotion saved = promotionRepository.save(promotion);

        if (promotion.getNumOfAvailable() != null) {
            redisService.set(getStockKey(promotion.getCode()), promotion.getNumOfAvailable().longValue());
        }

        if (promotion.getEndAt() != null) {
            long endAtMillis = promotion.getEndAt().toInstant(ZoneOffset.UTC).toEpochMilli();
            redisService.set(getExpireKey(promotion.getCode()), endAtMillis);
        }

        return promotionMapper.toResponse(saved);
    }

    @Override
    public PromotionResponse getPromotionById(String id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));
        return promotionMapper.toResponse(promotion);
    }

    @Override
    public List<PromotionResponse> getAllPromotions() {
        return promotionRepository.findAllByDeletedFalse()
                .stream()
                .map(promotionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deletePromotion(String id) {
        log.info("Deleting promotion with ID: {}", id);
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));

        String originalCode = promotion.getCode();
        promotion.setDeleted(true);
        // Đổi tên mã đã xóa mềm để giải phóng mã cho các chiến dịch tương lai mà không bị xung đột UNIQUE constraint
        promotion.setCode(originalCode + "_DELETED_" + System.currentTimeMillis());
        promotionRepository.save(promotion);

        redisService.delete(getStockKey(originalCode));
        redisService.delete(getExpireKey(originalCode));
        redisService.delete("promotion:applied:" + originalCode);
    }

    @Override
    public boolean isPromotionValid(String code) {
        if (code == null || code.isBlank()) return false;
        String cleanCode = code.trim().toUpperCase();
        ensureRedisState(cleanCode);
        String stockKey = getStockKey(cleanCode);
        String expireKey = getExpireKey(cleanCode);
        long now = Instant.now().toEpochMilli();
        return redisScriptService.checkPromotionCode(stockKey, expireKey, now);
    }

    @Override
    public boolean isPromotionValidForUser(String code, String userId) {
        if (code == null || code.isBlank()) {
            throw new AppException(ErrorCode.PROMOTION_NOT_FOUND);
        }
        String cleanCode = code.trim().toUpperCase();

        // 1. Kiểm tra Single Source of Truth trong MySQL trước
        if (userId != null && !userId.isBlank() &&
                promotionUsageRepository.existsByUserIdAndPromotionCodeAndStatus(userId, cleanCode, PromotionUsageStatus.USED)) {
            throw new AppException(ErrorCode.PROMOTION_ALREADY_USED);
        }

        ensureRedisState(cleanCode);
        String stockKey = getStockKey(cleanCode);
        String expireKey = getExpireKey(cleanCode);
        String appliedSetKey = "promotion:applied:" + cleanCode;

        int checkResult = redisScriptService.checkPromotionForUser(stockKey, expireKey, appliedSetKey, userId);
        return switch (checkResult) {
            case 1 -> true;
            case 0 -> throw new AppException(ErrorCode.PROMOTION_ALREADY_USED);
            case -1 -> throw new AppException(ErrorCode.PROMOTION_OUT_OF_STOCK);
            case -2 -> throw new AppException(ErrorCode.PROMOTION_EXPIRED);
            default -> throw new AppException(ErrorCode.PROMOTION_NOT_FOUND);
        };
    }

    @Override
    public boolean applyPromotionCode(String userId, String code) {
        if (code == null || code.isBlank()) return false;
        String cleanCode = code.trim().toUpperCase();

        // 1. Thẩm định Database trước (Chống trường hợp Redis bị sập hoặc mất cache)
        if (userId != null && !userId.isBlank() &&
                promotionUsageRepository.existsByUserIdAndPromotionCodeAndStatus(userId, cleanCode, PromotionUsageStatus.USED)) {
            throw new AppException(ErrorCode.PROMOTION_ALREADY_USED);
        }

        ensureRedisState(cleanCode);
        String stockKey = getStockKey(cleanCode);
        String expireKey = getExpireKey(cleanCode);
        String appliedSetKey = "promotion:applied:" + cleanCode;

        return redisScriptService.applyPromotionSafely(stockKey, expireKey, appliedSetKey, userId);
    }

    @Override
    public void rollbackPromotionCode(String userId, String code) {
        if (code == null || code.isBlank()) return;
        String cleanCode = code.trim().toUpperCase();
        String stockKey = getStockKey(cleanCode);
        String appliedSetKey = "promotion:applied:" + cleanCode;
        redisScriptService.rollbackPromotionSafely(stockKey, appliedSetKey, userId);
        log.info("Rolled back Redis promotion code [{}] for user [{}]", cleanCode, userId);
    }

    @Override
    public void recordPromotionUsage(Promotion promotion, String userId, String orderId, Double discountAmount) {
        PromotionUsage usage = PromotionUsage.builder()
                .promotion(promotion)
                .promotionCode(promotion.getCode())
                .userId(userId)
                .orderId(orderId)
                .discountAmount(discountAmount)
                .status(PromotionUsageStatus.USED)
                .build();
        promotionUsageRepository.save(usage);
        log.info("Recorded promotion usage in DB: promo={}, user={}, order={}, discount={}",
                promotion.getCode(), userId, orderId, discountAmount);
    }

    @Override
    public void rollbackPromotionUsage(String orderId) {
        promotionUsageRepository.findByOrderIdAndStatus(orderId, PromotionUsageStatus.USED)
                .ifPresent(usage -> {
                    usage.setStatus(PromotionUsageStatus.ROLLED_BACK);
                    promotionUsageRepository.save(usage);
                    rollbackPromotionCode(usage.getUserId(), usage.getPromotionCode());
                    log.info("Rolled back promotion usage for cancelled orderId: {}, code: {}, user: {}",
                            orderId, usage.getPromotionCode(), usage.getUserId());
                });
    }

    @Override
    public String generateUniqueCode(String prefix) {
        String cleanPrefix = (prefix != null && !prefix.isBlank()) ? prefix.trim().toUpperCase() : "SALE";
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        String generated;
        int attempts = 0;
        do {
            StringBuilder sb = new StringBuilder(cleanPrefix);
            if (!cleanPrefix.endsWith("-") && !cleanPrefix.isEmpty()) {
                sb.append("-");
            }
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            generated = sb.toString();
            attempts++;
            if (attempts > 50) {
                generated = cleanPrefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                break;
            }
        } while (promotionRepository.existsByCodeAndDeletedFalse(generated));

        return generated;
    }
}
