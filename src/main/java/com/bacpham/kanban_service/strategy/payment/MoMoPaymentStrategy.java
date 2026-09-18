package com.bacpham.kanban_service.strategy.payment;

import com.bacpham.kanban_service.configuration.payment.ConfigMoMo;
import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.PaymentResponse;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.strategy.discount.DiscountCalculator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Concrete PaymentStrategy cho cổng thanh toán MoMo.
 * Chuẩn hóa theo MoMo All-In-One (AIO) Gateway API v2:
 * - Ký HMAC-SHA256 theo đúng thứ tự tham số tài liệu MoMo.
 * - Gọi trực tiếp API MoMo tạo giao dịch hoặc fallback sang link thanh toán sandbox.
 * - Xác thực chữ ký callback / IPN chống giả mạo.
 * - Quản lý Idempotency & lưu cache tạm đơn hàng trong Redis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MoMoPaymentStrategy implements PaymentStrategy {

    private final ConfigMoMo configMoMo;
    private final GenericRedisService<String, String, String> redisService;
    private final GenericRedisService<String, String, OrderCreateRequest> redisServiceOrder;
    private final DiscountCalculator discountCalculator;
    private final RestTemplate restTemplate;

    @Override
    public PaymentType getPaymentType() {
        return PaymentType.MOMO;
    }

    @Override
    public PaymentResponse createPaymentUrl(OrderCreateRequest request, String userId, HttpServletRequest httpRequest) {
        double totalAmount = calculateTotal(request);
        long amount = (long) totalAmount;

        String partnerCode = configMoMo.getPartnerCode();
        String accessKey = configMoMo.getAccessKey();
        String secretKey = configMoMo.getSecretKey();
        String orderId = "MOMO" + System.currentTimeMillis();
        String requestId = orderId;
        String orderInfo = "Thanh toan don hang MoMo: " + orderId;
        String redirectUrl = configMoMo.getReturnUrl();
        String ipnUrl = configMoMo.getIpnUrl();
        String extraData = "";
        String requestType = "captureWallet";

        // Chuỗi dữ liệu raw theo quy định MoMo API v2
        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + requestType;

        String signature = ConfigMoMo.hmacSHA256(secretKey, rawSignature);

        String paymentUrl = null;

        // Thử gọi API MoMo v2 trực tiếp để lấy payUrl chính thức
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("partnerCode", partnerCode);
            requestBody.put("partnerName", "Kanban Store");
            requestBody.put("storeId", "KanbanStore");
            requestBody.put("requestId", requestId);
            requestBody.put("amount", amount);
            requestBody.put("orderId", orderId);
            requestBody.put("orderInfo", orderInfo);
            requestBody.put("redirectUrl", redirectUrl);
            requestBody.put("ipnUrl", ipnUrl);
            requestBody.put("lang", "vi");
            requestBody.put("extraData", extraData);
            requestBody.put("requestType", requestType);
            requestBody.put("signature", signature);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(configMoMo.getPayUrl(), entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map respBody = response.getBody();
                if (respBody.containsKey("payUrl")) {
                    paymentUrl = (String) respBody.get("payUrl");
                    log.info("Nhận được phản hồi tạo payUrl thành công từ MoMo API cho orderId: {}", orderId);
                }
            }
        } catch (Exception ex) {
            log.warn("Gọi API MoMo thất bại (chuyển sang cơ chế dự phòng sandbox/offline): {}", ex.getMessage());
        }

        // Nếu gọi MoMo API thất bại hoặc test offline, fallback sang redirect URL chứa chữ ký hợp lệ
        if (paymentUrl == null || paymentUrl.isBlank()) {
            paymentUrl = configMoMo.getPayUrl()
                    + "?partnerCode=" + partnerCode
                    + "&orderId=" + orderId
                    + "&requestId=" + requestId
                    + "&amount=" + amount
                    + "&orderInfo=" + URLEncoder.encode(orderInfo, StandardCharsets.UTF_8)
                    + "&redirectUrl=" + URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8)
                    + "&signature=" + signature;
        }

        // Lưu thông tin giao dịch tạm vào Redis
        redisService.set("payment:txnRef:" + orderId + ":userId", userId);
        redisService.setTimeToLive("payment:txnRef:" + orderId + ":userId", 15, TimeUnit.MINUTES);
        redisServiceOrder.set("payment:order:" + orderId, request);
        redisServiceOrder.setTimeToLive("payment:order:" + orderId, 15, TimeUnit.MINUTES);
        redisServiceOrder.set("payment:items:" + userId, request);
        redisServiceOrder.setTimeToLive("payment:items:" + userId, 15, TimeUnit.MINUTES);

        log.info("Tạo liên kết thanh toán MoMo cho mã đơn hàng: {}, partnerCode: {}", orderId, partnerCode);
        return PaymentResponse.builder().paymentUrl(paymentUrl).build();
    }

    @Override
    public PaymentCallbackResult handleCallback(Map<String, String> params) {
        String partnerCode = params.get("partnerCode");
        String orderId = params.get("orderId");
        String requestId = params.get("requestId");
        String amount = params.get("amount");
        String orderInfo = params.get("orderInfo");
        String orderType = params.get("orderType");
        String transId = params.get("transId");
        String resultCode = params.get("resultCode");
        String message = params.get("message");
        String payType = params.get("payType");
        String responseTime = params.get("responseTime");
        String extraData = params.get("extraData") != null ? params.get("extraData") : "";
        String signature = params.get("signature");

        if (orderId == null) {
            return new PaymentCallbackResult(false, false, "Không tìm thấy mã tham chiếu giao dịch (orderId) từ MoMo", null, null, null, null, null, false);
        }

        // Verify chữ ký HMAC-SHA256 MoMo v2 return
        String rawSignature = "accessKey=" + configMoMo.getAccessKey()
                + "&amount=" + (amount != null ? amount : "")
                + "&extraData=" + extraData
                + "&message=" + (message != null ? message : "")
                + "&orderId=" + orderId
                + "&orderInfo=" + (orderInfo != null ? orderInfo : "")
                + "&orderType=" + (orderType != null ? orderType : "")
                + "&partnerCode=" + (partnerCode != null ? partnerCode : "")
                + "&payType=" + (payType != null ? payType : "")
                + "&requestId=" + (requestId != null ? requestId : "")
                + "&responseTime=" + (responseTime != null ? responseTime : "")
                + "&resultCode=" + (resultCode != null ? resultCode : "")
                + "&transId=" + (transId != null ? transId : "");

        String expectedSignature = ConfigMoMo.hmacSHA256(configMoMo.getSecretKey(), rawSignature);
        boolean isValidSignature = signature != null && signature.equalsIgnoreCase(expectedSignature);

        if (!isValidSignature) {
            log.warn("Xác thực chữ ký MoMo thất bại cho mã đơn hàng: {}", orderId);
            return new PaymentCallbackResult(false, false, "Chữ ký không hợp lệ từ MoMo", transId, responseTime, orderId, null, null, false);
        }

        // Mã "0" trong MoMo đại diện cho giao dịch thành công
        boolean success = "0".equals(resultCode);
        String userId = null;
        OrderCreateRequest orderRequest = null;

        if (success) {
            // Kiểm tra Idempotency chống double callback
            String processed = redisService.get("payment:processed:" + orderId);
            if ("COMPLETED".equals(processed)) {
                log.info("Giao dịch MoMo {} đã được xử lý trước đó", orderId);
                return new PaymentCallbackResult(true, true, "Giao dịch đã được hoàn tất trước đó", transId, responseTime, orderId, null, null, true);
            }

            userId = redisService.get("payment:txnRef:" + orderId + ":userId");
            if (userId == null) {
                return new PaymentCallbackResult(false, true, "Không tìm thấy thông tin người dùng cho giao dịch: " + orderId, transId, responseTime, orderId, null, null, false);
            }
            orderRequest = redisServiceOrder.get("payment:order:" + orderId);
            if (orderRequest == null) {
                orderRequest = redisServiceOrder.get("payment:items:" + userId);
            }
            if (orderRequest == null) {
                return new PaymentCallbackResult(false, true, "Không tìm thấy chi tiết đơn hàng cho giao dịch: " + orderId, transId, responseTime, orderId, null, null, false);
            }
            log.info("Xác thực callback MoMo thành công cho mã đơn hàng: {}, userId: {}", orderId, userId);
        }

        String msg = success ? "Thanh toán thành công qua MoMo cho đơn hàng: " + orderId
                             : "Thanh toán thất bại qua MoMo cho đơn hàng: " + orderId + " (mã lỗi: " + resultCode + ", thông điệp: " + message + ")";
        return new PaymentCallbackResult(success, true, msg, transId, responseTime, orderId, userId, orderRequest, false);
    }

    private double calculateTotal(OrderCreateRequest request) {
        return request.getItems().stream()
                .mapToDouble(item -> discountCalculator.calculate(
                        item.getPrice(), item.getCount(), item.getDiscountValue()).itemTotal())
                .sum();
    }
}
