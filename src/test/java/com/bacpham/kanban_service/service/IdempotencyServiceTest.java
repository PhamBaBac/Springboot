package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.request.OrderItemRequest;
import com.bacpham.kanban_service.dto.response.IdempotencyLockResult;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.service.impl.IdempotencyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyServiceTest {

    private InMemoryRedisService redisService;
    private IdempotencyServiceImpl idempotencyService;

    static class InMemoryRedisService extends GenericRedisService<String, String, String> {
        private final Map<String, String> store = new HashMap<>();

        @Override
        public void set(String key, String value) {
            store.put(key, value);
        }

        @Override
        public Boolean setIfAbsent(String key, String value, Duration timeout) {
            if (store.containsKey(key)) {
                return false;
            }
            store.put(key, value);
            return true;
        }

        @Override
        public String get(String key) {
            return store.get(key);
        }

        @Override
        public void delete(String key) {
            store.remove(key);
        }

        @Override
        public void setTimeToLive(String key, long timeout, TimeUnit unit) {
        }
    }

    @BeforeEach
    void setUp() {
        redisService = new InMemoryRedisService();
        idempotencyService = new IdempotencyServiceImpl(redisService);
    }

    @Test
    @DisplayName("Thử lấy khóa lần đầu -> Thành công (acquired = true)")
    void testFirstAcquireSuccess() {
        IdempotencyLockResult result = idempotencyService.tryAcquire("req-123", "order_create", Duration.ofMinutes(2));

        assertTrue(result.isAcquired());
        assertFalse(result.isAlreadyCompleted());
        assertEquals("IN_PROGRESS", redisService.get("idempotency:order_create:req-123"));
    }

    @Test
    @DisplayName("Gửi lặp lại cùng key khi đang IN_PROGRESS -> Ném AppException ORDER_PROCESSING_IN_PROGRESS")
    void testDuplicateConcurrentAcquireThrowsException() {
        idempotencyService.tryAcquire("req-123", "order_create", Duration.ofMinutes(2));

        AppException ex = assertThrows(AppException.class, () ->
                idempotencyService.tryAcquire("req-123", "order_create", Duration.ofMinutes(2)));

        assertEquals(ErrorCode.ORDER_PROCESSING_IN_PROGRESS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Gửi lại sau khi đã COMPLETED -> Trả về alreadyCompleted=true kèm kết quả cũ")
    void testAcquireAfterCompletedReturnsCachedResult() {
        idempotencyService.tryAcquire("req-123", "order_create", Duration.ofMinutes(2));
        idempotencyService.markCompleted("req-123", "order_create", "order-abc-999", Duration.ofHours(24));

        IdempotencyLockResult retryResult = idempotencyService.tryAcquire("req-123", "order_create", Duration.ofMinutes(2));

        assertFalse(retryResult.isAcquired());
        assertTrue(retryResult.isAlreadyCompleted());
        assertEquals("order-abc-999", retryResult.getResultData());
    }

    @Test
    @DisplayName("Giải phóng khóa khi có lỗi -> Cho phép thực hiện lại với key đó")
    void testReleaseAllowsReacquire() {
        idempotencyService.tryAcquire("req-fail", "order_create", Duration.ofMinutes(2));
        idempotencyService.release("req-fail", "order_create");

        assertNull(redisService.get("idempotency:order_create:req-fail"));

        IdempotencyLockResult retryResult = idempotencyService.tryAcquire("req-fail", "order_create", Duration.ofMinutes(2));
        assertTrue(retryResult.isAcquired());
    }

    @Test
    @DisplayName("Sinh fingerprint tự động: Cùng payload -> Cùng hash; khác item/user -> Khác hash")
    void testGenerateFingerprint() {
        OrderCreateRequest req1 = OrderCreateRequest.builder()
                .addressId("addr-1")
                .items(List.of(
                        OrderItemRequest.builder().subProductId("sp-1").count(2).price(100.0).build(),
                        OrderItemRequest.builder().subProductId("sp-2").count(1).price(50.0).build()
                ))
                .build();

        OrderCreateRequest req2 = OrderCreateRequest.builder()
                .addressId("addr-1")
                .items(List.of(
                        OrderItemRequest.builder().subProductId("sp-2").count(1).price(50.0).build(),
                        OrderItemRequest.builder().subProductId("sp-1").count(2).price(100.0).build()
                ))
                .build();

        String hash1 = idempotencyService.generateFingerprint("user-1", "COD", req1);
        String hash2 = idempotencyService.generateFingerprint("user-1", "COD", req2);

        assertEquals(hash1, hash2, "Thứ tự items khác nhau nhưng cùng nội dung phải sinh cùng hash");

        String hashDiffUser = idempotencyService.generateFingerprint("user-2", "COD", req1);
        assertNotEquals(hash1, hashDiffUser, "Khác user phải sinh khác hash");
    }
}
