package com.bacpham.kanban_service.strategy.payment;

import com.bacpham.kanban_service.configuration.payment.ConfigMoMo;
import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.request.OrderItemRequest;
import com.bacpham.kanban_service.dto.response.PaymentResponse;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.strategy.discount.DiscountCalculator;
import com.bacpham.kanban_service.strategy.discount.DiscountStrategy;
import com.bacpham.kanban_service.strategy.discount.FixedAmountDiscountStrategy;
import com.bacpham.kanban_service.strategy.discount.PercentageDiscountStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MoMoPaymentStrategyTest {

    private MoMoPaymentStrategy moMoStrategy;
    private ConfigMoMo configMoMo;
    private InMemoryRedisService<String> redisService;
    private InMemoryRedisService<OrderCreateRequest> redisServiceOrder;

    private final String partnerCode = "MOMO";
    private final String accessKey = "F8BBA842ECF85";
    private final String secretKey = "K951B6PE1waDMi640xX0huTrY0hs6AHQ";

    static class InMemoryRedisService<V> extends GenericRedisService<String, String, V> {
        private final Map<String, V> store = new ConcurrentHashMap<>();

        @Override
        public void set(String key, V value) {
            store.put(key, value);
        }

        @Override
        public V get(String key) {
            return store.get(key);
        }

        @Override
        public void setTimeToLive(String key, long timeout, TimeUnit timeUnit) {
        }

        @Override
        public void delete(String key) {
            store.remove(key);
        }
    }

    @BeforeEach
    void setUp() {
        configMoMo = new ConfigMoMo();
        ReflectionTestUtils.setField(configMoMo, "partnerCode", partnerCode);
        ReflectionTestUtils.setField(configMoMo, "accessKey", accessKey);
        ReflectionTestUtils.setField(configMoMo, "secretKey", secretKey);
        ReflectionTestUtils.setField(configMoMo, "payUrl", "https://test-payment.momo.vn/v2/gateway/api/create");
        ReflectionTestUtils.setField(configMoMo, "returnUrl", "http://localhost:8080/api/v1/payment/momo-return");
        ReflectionTestUtils.setField(configMoMo, "ipnUrl", "http://localhost:8080/api/v1/payment/momo-ipn");

        redisService = new InMemoryRedisService<>();
        redisServiceOrder = new InMemoryRedisService<>();

        DiscountCalculator discountCalculator = new DiscountCalculator(
                List.<DiscountStrategy>of(new FixedAmountDiscountStrategy(), new PercentageDiscountStrategy())
        );
        RestTemplate restTemplate = new RestTemplate();

        moMoStrategy = new MoMoPaymentStrategy(configMoMo, redisService, redisServiceOrder, discountCalculator, restTemplate);
    }

    @Test
    @DisplayName("PaymentType phải là MOMO")
    void testGetPaymentType() {
        assertEquals(PaymentType.MOMO, moMoStrategy.getPaymentType());
    }

    @Test
    @DisplayName("Tạo Payment URL cho MoMo thành công và lưu cache Redis")
    void testCreatePaymentUrl() {
        OrderItemRequest item = new OrderItemRequest();
        item.setPrice(100000.0);
        item.setCount(2);

        OrderCreateRequest request = new OrderCreateRequest();
        request.setAddressId("addr_123");
        request.setItems(List.of(item));

        PaymentResponse response = moMoStrategy.createPaymentUrl(request, "user_123", null);

        assertNotNull(response);
        assertNotNull(response.getPaymentUrl());
        assertTrue(response.getPaymentUrl().contains("partnerCode=MOMO"));
        assertTrue(response.getPaymentUrl().contains("signature="));
    }

    @Test
    @DisplayName("Callback MoMo với chữ ký hợp lệ và resultCode = 0 -> Thành công")
    void testHandleCallbackSuccess() {
        String orderId = "MOMO_TEST_001";
        String requestId = orderId;
        String amount = "100000";
        String orderInfo = "Thanh toan don hang MoMo: " + orderId;
        String orderType = "momo_wallet";
        String transId = "9988776655";
        String resultCode = "0";
        String message = "Successful.";
        String payType = "qr";
        String responseTime = "1710738000000";
        String extraData = "";

        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&message=" + message
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&orderType=" + orderType
                + "&partnerCode=" + partnerCode
                + "&payType=" + payType
                + "&requestId=" + requestId
                + "&responseTime=" + responseTime
                + "&resultCode=" + resultCode
                + "&transId=" + transId;

        String signature = ConfigMoMo.hmacSHA256(secretKey, rawSignature);

        Map<String, String> params = new HashMap<>();
        params.put("partnerCode", partnerCode);
        params.put("orderId", orderId);
        params.put("requestId", requestId);
        params.put("amount", amount);
        params.put("orderInfo", orderInfo);
        params.put("orderType", orderType);
        params.put("transId", transId);
        params.put("resultCode", resultCode);
        params.put("message", message);
        params.put("payType", payType);
        params.put("responseTime", responseTime);
        params.put("extraData", extraData);
        params.put("signature", signature);

        OrderCreateRequest orderRequest = new OrderCreateRequest();
        redisService.set("payment:txnRef:" + orderId + ":userId", "user_123");
        redisServiceOrder.set("payment:order:" + orderId, orderRequest);

        PaymentCallbackResult result = moMoStrategy.handleCallback(params);

        assertTrue(result.signatureValid());
        assertTrue(result.success());
        assertEquals("user_123", result.userId());
        assertEquals(orderRequest, result.orderRequest());
        assertEquals(orderId, result.txnRef());
        assertFalse(result.alreadyProcessed());
    }

    @Test
    @DisplayName("Callback MoMo với chữ ký bị giả mạo -> Signature invalid")
    void testHandleCallbackInvalidSignature() {
        Map<String, String> params = new HashMap<>();
        params.put("partnerCode", partnerCode);
        params.put("orderId", "MOMO_TEST_002");
        params.put("amount", "50000");
        params.put("resultCode", "0");
        params.put("signature", "FORGED_SIGNATURE_12345");

        PaymentCallbackResult result = moMoStrategy.handleCallback(params);

        assertFalse(result.signatureValid());
        assertFalse(result.success());
        assertEquals("Invalid checksum from MoMo", result.message());
    }

    @Test
    @DisplayName("Callback MoMo đã được xử lý trước đó -> Idempotent check trả về alreadyProcessed = true")
    void testHandleCallbackAlreadyProcessed() {
        String orderId = "MOMO_TEST_003";
        String rawSignature = "accessKey=" + accessKey
                + "&amount=100000"
                + "&extraData="
                + "&message=Success"
                + "&orderId=" + orderId
                + "&orderInfo=Test"
                + "&orderType=momo_wallet"
                + "&partnerCode=" + partnerCode
                + "&payType=qr"
                + "&requestId=" + orderId
                + "&responseTime=1710738000000"
                + "&resultCode=0"
                + "&transId=123";

        String signature = ConfigMoMo.hmacSHA256(secretKey, rawSignature);

        Map<String, String> params = new HashMap<>();
        params.put("partnerCode", partnerCode);
        params.put("orderId", orderId);
        params.put("requestId", orderId);
        params.put("amount", "100000");
        params.put("orderInfo", "Test");
        params.put("orderType", "momo_wallet");
        params.put("transId", "123");
        params.put("resultCode", "0");
        params.put("message", "Success");
        params.put("payType", "qr");
        params.put("responseTime", "1710738000000");
        params.put("signature", signature);

        redisService.set("payment:processed:" + orderId, "COMPLETED");

        PaymentCallbackResult result = moMoStrategy.handleCallback(params);

        assertTrue(result.signatureValid());
        assertTrue(result.success());
        assertTrue(result.alreadyProcessed());
    }
}
