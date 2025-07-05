package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.configuration.payment.ConfigVNPay;
import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.PaymentResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.impl.OrderServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/api/v1/payment")
public class PaymentVNPayController {
    private final UserRepository userRepository;
    private final OrderServiceImpl orderService;
    private final GenericRedisService<String, String, String> redisService;
    private final GenericRedisService<String, String, OrderCreateRequest> redisServiceOrder;

    public PaymentVNPayController(
            UserRepository userRepository,
            OrderServiceImpl orderService,
            GenericRedisService<String, String, String> redisService,
            GenericRedisService<String, String, OrderCreateRequest> redisServiceOrder) {
        this.userRepository = userRepository;
        this.orderService = orderService;
        this.redisService = redisService;
        this.redisServiceOrder = redisServiceOrder;
    }

    @PostMapping("/create")
    @ResponseBody
    public ApiResponse<PaymentResponse> createPayment(
            @RequestBody OrderCreateRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String userId = user.getId();

        // Tính tổng tiền đơn hàng
        double totalAmount = request.getItems().stream()
                .mapToDouble(item -> item.getPrice() * item.getCount())
                .sum();

//        String bankCode = "NCB";
        String language = "vn";
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String orderType = "other";
        long vnp_Amount = (long) (totalAmount * 100L); // nhân 100 theo chuẩn VNPay
        String vnp_TxnRef = ConfigVNPay.getRandomNumber(8);
        String vnp_IpAddr = ConfigVNPay.getIpAddress(httpRequest);
        String vnp_TmnCode = ConfigVNPay.vnp_TmnCode;

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(vnp_Amount));
        vnp_Params.put("vnp_CurrCode", "VND");
//        vnp_Params.put("vnp_BankCode", bankCode);
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang:" + vnp_TxnRef);
        vnp_Params.put("vnp_OrderType", orderType);
        vnp_Params.put("vnp_Locale", language);
        vnp_Params.put("vnp_ReturnUrl", ConfigVNPay.vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        // Thời gian tạo giao dịch
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        // Thời gian hết hạn
        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // Tạo query + hash
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII))
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    hashData.append('&');
                    query.append('&');
                }
            }
        }
        String vnp_SecureHash = ConfigVNPay.hmacSHA512(ConfigVNPay.vnp_HashSecret, hashData.toString());
        String queryUrl = query + "&vnp_SecureHash=" + vnp_SecureHash;
        String paymentUrl = ConfigVNPay.vnp_PayUrl + "?" + queryUrl;

        // Lưu mapping TxnRef -> userId để vnpay-return tìm ra user
        redisService.set("payment:txnRef:" + vnp_TxnRef + ":userId", userId);
        redisService.setTimeToLive("payment:txnRef:" + vnp_TxnRef + ":userId", 15, TimeUnit.MINUTES);

        // Lưu request order của user
        redisServiceOrder.set("payment:items:" + userId, request);
        redisServiceOrder.setTimeToLive("payment:items:" + userId, 15, TimeUnit.MINUTES);

        return ApiResponse.<PaymentResponse>builder()
                .code(200)
                .message("Payment URL created successfully")
                .result(PaymentResponse.builder().paymentUrl(paymentUrl).build())
                .build();
    }

    @GetMapping("/vnpay-return")
    public String vnpayReturn(
            @RequestParam Map<String, String> params,
            Model model) {

        String vnp_ResponseCode = params.get("vnp_ResponseCode");
        String vnp_TxnRef = params.get("vnp_TxnRef");
        String vnp_TransactionNo = params.get("vnp_TransactionNo");
        String vnp_PayDate = params.get("vnp_PayDate");
        String paymentType = "VNPAY";

        if (vnp_TxnRef == null) {
            model.addAttribute("message", "Transaction reference not provided by VNPAY");
            return "payment-result.html";
        }

        // Tìm userId từ vnp_TxnRef
        String userId = redisService.get("payment:txnRef:" + vnp_TxnRef + ":userId");
        if (userId == null) {
            model.addAttribute("message", "Cannot find user info for transaction: " + vnp_TxnRef);
            return "payment-result.html";
        }

        // Lấy request từ Redis
        OrderCreateRequest request = redisServiceOrder.get("payment:items:" + userId);
        if (request == null) {
            model.addAttribute("message", "Cannot find order details for user: " + userId);
            return "payment-result.html";
        }

        if ("00".equals(vnp_ResponseCode)) {
            orderService.createOrderFromSelectedItems(userId, paymentType, request);
            model.addAttribute("message", "Payment successful for transaction: " + vnp_TxnRef + " at " + vnp_PayDate);
            model.addAttribute("transactionNo", vnp_TransactionNo);
        } else {
            model.addAttribute("message", "Payment failed for transaction: " + vnp_TxnRef);
            model.addAttribute("responseCode", vnp_ResponseCode);
        }
        return "payment-result.html";
    }
}
