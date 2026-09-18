package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.configuration.payment.ConfigVNPay;
import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.DiscountRequest;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.request.OrderItemRequest;
import com.bacpham.kanban_service.dto.response.PaymentResponse;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.service.IVNPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Implementation cua IVNPayService.
 * Chuyen toan bo business logic tu PaymentVNPayController vao day de tuan thu SRP.
 * Controller gio chi co nhiem vu nhan request / tra response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VNPayServiceImpl implements IVNPayService {

    private final GenericRedisService<String, String, String> redisService;
    private final GenericRedisService<String, String, OrderCreateRequest> redisServiceOrder;

    @Override
    public PaymentResponse createPaymentUrl(OrderCreateRequest request, String userId, HttpServletRequest httpRequest) {
        double totalAmount = calculateTotal(request);

        String language = "vn";
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String orderType = "other";
        long vnp_Amount = (long) (totalAmount * 100);
        String vnp_TxnRef = ConfigVNPay.getRandomNumber(8);
        String vnp_IpAddr = ConfigVNPay.getIpAddress(httpRequest);
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

        // Luu mapping vao Redis
        redisService.set("payment:txnRef:" + vnp_TxnRef + ":userId", userId);
        redisService.setTimeToLive("payment:txnRef:" + vnp_TxnRef + ":userId", 15, TimeUnit.MINUTES);
        redisServiceOrder.set("payment:order:" + vnp_TxnRef, request);
        redisServiceOrder.setTimeToLive("payment:order:" + vnp_TxnRef, 15, TimeUnit.MINUTES);
        redisServiceOrder.set("payment:items:" + userId, request);
        redisServiceOrder.setTimeToLive("payment:items:" + userId, 15, TimeUnit.MINUTES);

        log.info("VNPay paymentUrl created for txnRef: {}, tmnCode: {}", vnp_TxnRef, vnp_TmnCode);
        return PaymentResponse.builder().paymentUrl(paymentUrl).build();
    }

    @Override
    public VNPayCallbackResult handleCallback(Map<String, String> params) {
        String vnp_ResponseCode = params.get("vnp_ResponseCode");
        String vnp_TxnRef = params.get("vnp_TxnRef");
        String vnp_TransactionNo = params.get("vnp_TransactionNo");
        String vnp_PayDate = params.get("vnp_PayDate");
        String vnp_SecureHash = params.get("vnp_SecureHash");

        if (vnp_TxnRef == null) {
            return new VNPayCallbackResult(false, false, "Transaction reference not provided by VNPAY", null, null, null, null, null, false);
        }

        // Verify chu ky
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
            log.warn("VNPay signature verification failed for txnRef: {}", vnp_TxnRef);
            return new VNPayCallbackResult(false, false, "Invalid checksum from VNPay", vnp_TransactionNo, vnp_PayDate, vnp_TxnRef, null, null, false);
        }

        boolean success = "00".equals(vnp_ResponseCode);
        String userId = null;
        OrderCreateRequest orderRequest = null;

        if (success) {
            // Kiểm tra Idempotency: chống race condition khi user refresh hoặc double callback
            String processed = redisService.get("payment:processed:" + vnp_TxnRef);
            if ("COMPLETED".equals(processed)) {
                log.info("VNPay transaction {} has already been processed (Idempotent call)", vnp_TxnRef);
                return new VNPayCallbackResult(true, true, "Transaction already completed", vnp_TransactionNo, vnp_PayDate, vnp_TxnRef, null, null, true);
            }

            // Lay thong tin order tu Redis
            userId = redisService.get("payment:txnRef:" + vnp_TxnRef + ":userId");
            if (userId == null) {
                return new VNPayCallbackResult(false, true, "Cannot find user info for transaction: " + vnp_TxnRef, vnp_TransactionNo, vnp_PayDate, vnp_TxnRef, null, null, false);
            }
            orderRequest = redisServiceOrder.get("payment:order:" + vnp_TxnRef);
            if (orderRequest == null) {
                orderRequest = redisServiceOrder.get("payment:items:" + userId);
            }
            if (orderRequest == null) {
                return new VNPayCallbackResult(false, true, "Cannot find order details for transaction: " + vnp_TxnRef, vnp_TransactionNo, vnp_PayDate, vnp_TxnRef, null, null, false);
            }
            log.info("VNPay callback validated successfully for txnRef: {}, userId: {}", vnp_TxnRef, userId);
        }

        String msg = success ? "Payment successful for transaction: " + vnp_TxnRef + " at " + vnp_PayDate
                             : "Payment failed for transaction: " + vnp_TxnRef;
        return new VNPayCallbackResult(success, true, msg, vnp_TransactionNo, vnp_PayDate, vnp_TxnRef, userId, orderRequest, false);
    }

    // ===== Private helper methods =====

    /**
     * Tinh tong tien don hang sau giam gia.
     */
    private double calculateTotal(OrderCreateRequest request) {
        return request.getItems().stream()
                .mapToDouble(item -> {
                    double itemTotal = item.getPrice() * item.getCount();
                    DiscountRequest discount = item.getDiscountValue();
                    if (discount != null && discount.getValue() != null && discount.getType() != null) {
                        try {
                            double discountValue = Double.parseDouble(discount.getValue());
                            switch (discount.getType()) {
                                case DISCOUNT -> itemTotal -= discountValue;
                                case PERCENT -> itemTotal *= (1 - discountValue / 100.0);
                                default -> throw new AppException(ErrorCode.INVALID_PROMOTION_TYPE);
                            }
                        } catch (NumberFormatException e) {
                            throw new AppException(ErrorCode.INVALID_PROMOTION_VALUE);
                        }
                    }
                    return Math.max(0, itemTotal);
                })
                .sum();
    }

    /**
     * Xay dung payment URL tu map params VNPay.
     */
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