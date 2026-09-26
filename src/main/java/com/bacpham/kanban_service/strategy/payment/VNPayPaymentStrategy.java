package com.bacpham.kanban_service.strategy.payment;

import com.bacpham.kanban_service.configuration.payment.ConfigVNPay;
import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.PaymentResponse;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.strategy.discount.DiscountCalculator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Concrete PaymentStrategy cho cổng thanh toán VNPay.
 * Đóng gói toàn bộ nghiệp vụ tạo URL mã hóa HMAC-SHA512 và xác thực chữ ký callback của VNPay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VNPayPaymentStrategy implements PaymentStrategy {

    private final GenericRedisService<String, String, String> redisService;
    private final GenericRedisService<String, String, OrderCreateRequest> redisServiceOrder;
    private final DiscountCalculator discountCalculator;

    @Override
    public PaymentType getPaymentType() {
        return PaymentType.VNPAY;
    }

    @Override
    public PaymentResponse createPaymentUrl(OrderCreateRequest request, String userId, HttpServletRequest httpRequest) {
        double totalAmount = calculateTotal(request);

        String language = "vn";
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String orderType = "other";
        long vnp_Amount = (long) (totalAmount * 100);
        String vnp_TxnRef = ConfigVNPay.getRandomNumber(8);
        String vnp_IpAddr = httpRequest != null ? ConfigVNPay.getIpAddress(httpRequest) : "127.0.0.1";
        String vnp_TmnCode = ConfigVNPay.vnp_TmnCode;

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(vnp_Amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang:" + vnp_TxnRef);
        vnp_Params.put("vnp_OrderType", orderType);
        vnp_Params.put("vnp_Locale", language);
        vnp_Params.put("vnp_ReturnUrl", ConfigVNPay.vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));
        cld.add(Calendar.MINUTE, 15);
        vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));

        String paymentUrl = buildPaymentUrl(vnp_Params);

        redisService.set("payment:txnRef:" + vnp_TxnRef + ":userId", userId);
        redisService.setTimeToLive("payment:txnRef:" + vnp_TxnRef + ":userId", 15, TimeUnit.MINUTES);
        redisServiceOrder.set("payment:order:" + vnp_TxnRef, request);
        redisServiceOrder.setTimeToLive("payment:order:" + vnp_TxnRef, 15, TimeUnit.MINUTES);
        redisServiceOrder.set("payment:items:" + userId, request);
        redisServiceOrder.setTimeToLive("payment:items:" + userId, 15, TimeUnit.MINUTES);

        log.info("Tạo liên kết thanh toán VNPay cho mã giao dịch: {}, tmnCode: {}", vnp_TxnRef, vnp_TmnCode);
        return PaymentResponse.builder().paymentUrl(paymentUrl).build();
    }

    @Override
    public PaymentCallbackResult handleCallback(Map<String, String> params) {
        String vnp_ResponseCode = params.get("vnp_ResponseCode");
        String vnp_TxnRef = params.get("vnp_TxnRef");
        String vnp_TransactionNo = params.get("vnp_TransactionNo");
        String vnp_PayDate = params.get("vnp_PayDate");
        String vnp_SecureHash = params.get("vnp_SecureHash");

        if (vnp_TxnRef == null) {
            return new PaymentCallbackResult(false, false, "Không tìm thấy mã tham chiếu giao dịch từ VNPAY", null, null, null, null, null, false);
        }

        Map<String, String> fields = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                fields.put(entry.getKey(), entry.getValue());
            }
        }
        fields.remove("vnp_SecureHashType");
        fields.remove("vnp_SecureHash");

        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII)).append('&');
            }
        }
        if (hashData.length() > 0) hashData.setLength(hashData.length() - 1);

        String signValue = ConfigVNPay.hmacSHA512(ConfigVNPay.vnp_HashSecret, hashData.toString());
        boolean isValidSignature = signValue.equalsIgnoreCase(vnp_SecureHash);

        if (!isValidSignature) {
            log.warn("Xác thực chữ ký VNPay thất bại cho mã giao dịch: {}", vnp_TxnRef);
            return new PaymentCallbackResult(false, false, "Chữ ký không hợp lệ từ VNPay", vnp_TransactionNo, vnp_PayDate, vnp_TxnRef, null, null, false);
        }

        boolean success = "00".equals(vnp_ResponseCode);
        String userId = null;
        OrderCreateRequest orderRequest = null;

        if (success) {
            String processed = redisService.get("payment:processed:" + vnp_TxnRef);
            if ("COMPLETED".equals(processed)) {
                log.info("Giao dịch VNPay {} đã được xử lý trước đó", vnp_TxnRef);
                return new PaymentCallbackResult(true, true, "Giao dịch đã được hoàn tất trước đó", vnp_TransactionNo, vnp_PayDate, vnp_TxnRef, null, null, true);
            }

            userId = redisService.get("payment:txnRef:" + vnp_TxnRef + ":userId");
            if (userId == null) {
                return new PaymentCallbackResult(false, true, "Không tìm thấy thông tin người dùng cho giao dịch: " + vnp_TxnRef, vnp_TransactionNo, vnp_PayDate, vnp_TxnRef, null, null, false);
            }
            orderRequest = redisServiceOrder.get("payment:order:" + vnp_TxnRef);
            if (orderRequest == null) {
                orderRequest = redisServiceOrder.get("payment:items:" + userId);
            }
            if (orderRequest == null) {
                return new PaymentCallbackResult(false, true, "Không tìm thấy chi tiết đơn hàng cho giao dịch: " + vnp_TxnRef, vnp_TransactionNo, vnp_PayDate, vnp_TxnRef, null, null, false);
            }
            log.info("Xác thực callback VNPay thành công cho mã giao dịch: {}, userId: {}", vnp_TxnRef, userId);
        }

        String msg = success ? "Thanh toán thành công cho giao dịch: " + vnp_TxnRef + " vào lúc " + vnp_PayDate
                             : "Thanh toán thất bại cho giao dịch: " + vnp_TxnRef;
        return new PaymentCallbackResult(success, true, msg, vnp_TransactionNo, vnp_PayDate, vnp_TxnRef, userId, orderRequest, false);
    }

    private double calculateTotal(OrderCreateRequest request) {
        return request.getItems().stream()
                .mapToDouble(item -> discountCalculator.calculate(
                        item.getPrice(), item.getCount(), item.getDiscountValue()).itemTotal())
                .sum();
    }

    private String buildPaymentUrl(Map<String, String> vnp_Params) {
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII)).append('&');
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII)).append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII)).append('&');
            }
        }
        if (hashData.length() > 0) hashData.setLength(hashData.length() - 1);
        if (query.length() > 0) query.setLength(query.length() - 1);

        String vnp_SecureHash = ConfigVNPay.hmacSHA512(ConfigVNPay.vnp_HashSecret, hashData.toString());
        return ConfigVNPay.vnp_PayUrl + "?" + query + "&vnp_SecureHash=" + vnp_SecureHash;
    }
}
