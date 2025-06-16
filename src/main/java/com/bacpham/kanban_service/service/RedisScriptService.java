package com.bacpham.kanban_service.service;


import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;

@Service
public class RedisScriptService {
    private final GenericRedisService<String, String, Long> redisService;
    private final RedisScript<Long> stockDecrementScript;
    private final RedisScript<Long> rollbackStockScript;
    private final RedisScript<Long> checkExpiredAndStockScript;
    private final RedisScript<Long> applyCodeOnceScript;
    private RedisScript<Long> applyPromotionSafelyScript;

    public RedisScriptService(
            GenericRedisService<String, String, Long> redisService,
            @Qualifier("stockDecrementScript") RedisScript<Long> stockDecrementScript,
            @Qualifier("rollbackStockScript") RedisScript<Long> rollbackStockScript,
            @Qualifier("checkExpiredAndStockScript") RedisScript<Long> checkExpiredAndStockScript,
            @Qualifier("applyCodeOnceScript") RedisScript<Long> applyCodeOnceScript
            , @Qualifier("applyPromotionSafelyScript") RedisScript<Long> applyPromotionSafelyScript
    ) {
        this.redisService = redisService;
        this.stockDecrementScript = stockDecrementScript;
        this.rollbackStockScript = rollbackStockScript;
        this.checkExpiredAndStockScript = checkExpiredAndStockScript;
        this.applyCodeOnceScript = applyCodeOnceScript;
        this.applyPromotionSafelyScript = applyPromotionSafelyScript;
    }
    public boolean decrementStock(String key) {
        Long result = redisService.executeLuaScript(stockDecrementScript, List.of(key), List.of());
        return result != null && result == 1L;
    }

    public void rollbackStock(String key) {
        redisService.executeLuaScript(rollbackStockScript, List.of(key), List.of());
    }

    public boolean checkStockAndNotExpired(String stockKey, String expireKey, long nowMillis) {
        Long result = redisService.executeLuaScript(checkExpiredAndStockScript, List.of(stockKey, expireKey), List.of(nowMillis));
        return result != null && result == 1L;
    }

    public boolean applyPromotionSafely(String stockKey, String expireKey, String appliedSetKey, String userId) {
        long now = Instant.now().toEpochMilli();

        List<String> keys = List.of(stockKey, expireKey, appliedSetKey);
        List<Object> args = List.of(now, userId);

        Long result = redisService.executeLuaScript(applyPromotionSafelyScript, keys, args);
        if (result == null) return false;

        return switch (result.intValue()) {
            case 1 -> true; // success
            case 0 -> throw new AppException(ErrorCode.PROMOTION_ALREADY_USED);
            case -1 -> throw new AppException(ErrorCode.PROMOTION_OUT_OF_STOCK);
            case -2 -> throw new AppException(ErrorCode.PROMOTION_EXPIRED);
            default -> throw new AppException(ErrorCode.UNKNOWN);
        };
    }
}
