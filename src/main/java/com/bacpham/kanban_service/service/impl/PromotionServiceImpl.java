package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.PromotionRequest;
import com.bacpham.kanban_service.dto.response.PromotionResponse;
import com.bacpham.kanban_service.entity.Promotion;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.PromotionMapper;
import com.bacpham.kanban_service.repository.PromotionRepository;
import com.bacpham.kanban_service.service.IPromotionService;
import com.bacpham.kanban_service.service.RedisScriptService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionServiceImpl implements IPromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionMapper promotionMapper;
    private final GenericRedisService<String, String, Long> redisService;
    private final RedisScriptService redisScriptService;

    private String getStockKey(String code) {
        return "promotion:stock:" + code;
    }

    private String getExpireKey(String code) {
        return "promotion:expire:" + code;
    }

    private String getUserKey(String userId) {
        return "promotion:applied:" + userId;
    }

    @Override
    public PromotionResponse createPromotion(PromotionRequest request) {
        log.info("Creating promotion: {}", request);

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
    @Override public PromotionResponse getPromotionByNameCode(String code) {
        Promotion promotion = promotionRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));
        return promotionMapper.toResponse(promotion);
    }

    @Override
    public PromotionResponse updatePromotion(String id, PromotionRequest request) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));

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
        return promotionRepository.findAll().stream()
                .map(promotionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deletePromotion(String id) {
        log.info("Deleting promotion with ID: {}", id);
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));

        promotion.setDeleted(true);
        promotionRepository.save(promotion);

        String code = promotion.getCode();

        redisService.delete(getStockKey(promotion.getCode()));
        redisService.delete(getExpireKey(promotion.getCode()));
        redisService.delete("promotion:applied:" + code);
    }
    @Override
    public boolean isPromotionValid(String code) {
        String stockKey = getStockKey(code);
        String expireKey = getExpireKey(code);
        long now = Instant.now().toEpochMilli();
        return redisScriptService.checkStockAndNotExpired(stockKey, expireKey, now);
    }
    @Override
    public boolean applyPromotionCode(String userId, String code) {
        String stockKey = getStockKey(code);
        String expireKey = getExpireKey(code);
        String appliedSetKey = "promotion:applied:" + code;
        return redisScriptService.applyPromotionSafely(stockKey, expireKey, appliedSetKey, userId);
    }

}
