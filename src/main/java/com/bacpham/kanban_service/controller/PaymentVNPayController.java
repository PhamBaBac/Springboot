package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.PaymentResponse;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.service.IOrderService;
import com.bacpham.kanban_service.service.IVNPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PaymentVNPayController sau khi refactor theo SRP:
 * - Chi xu ly HTTP request / response.
 * - Delegate business logic (tao URL, verify hash, tinh tien) sang IVNPayService.
 * - Delegate tao order sang IOrderService.
 */
@Slf4j
@Controller
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentVNPayController {

    private final UserRepository userRepository;
    private final IOrderService orderService;
    private final IVNPayService vnPayService;
    private final GenericRedisService<String, String, OrderCreateRequest> redisServiceOrder;
    private final GenericRedisService<String, String, String> redisService;

    @PostMapping("/create")
    @ResponseBody
    public ApiResponse<PaymentResponse> createPayment(
            @RequestBody OrderCreateRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        PaymentResponse paymentResponse = vnPayService.createPaymentUrl(request, user.getId(), httpRequest);

        return ApiResponse.<PaymentResponse>builder()
                .code(200)
                .message("Payment URL created successfully")
                .data(paymentResponse)
                .build();
    }

    @GetMapping("/vnpay-return")
    public String vnpayReturn(
            @RequestParam Map<String, String> params,
            Model model) {

        log.info("VNPay return callback received: txnRef={}, responseCode={}",
                params.get("vnp_TxnRef"), params.get("vnp_ResponseCode"));

        IVNPayService.VNPayCallbackResult result = vnPayService.handleCallback(params);

        if (!result.signatureValid()) {
            model.addAttribute("message", result.message());
            model.addAttribute("responseCode", "INVALID_SIGNATURE");
            return "payment-result.html";
        }

        if (result.txnRef() == null) {
            model.addAttribute("message", result.message());
            return "payment-result.html";
        }

        if (result.success()) {
            // A-7 Idempotency & Concurrency check:
            // Nếu transaction đã hoàn tất trước đó (user bấm F5 refresh trang), chỉ hiển thị thông tin thành công, không tạo đơn lần 2
            if (result.alreadyProcessed()) {
                log.info("VNPay return already processed for txnRef: {}", result.txnRef());
                model.addAttribute("message", "Đơn hàng đã được ghi nhận và tạo thành công trước đó.");
                model.addAttribute("transactionNo", result.transactionNo());
                return "payment-result.html";
            }

            // Tạo đơn hàng an toàn từ payload đã được validate bởi VNPayService
            if (result.orderRequest() != null && result.userId() != null) {
                orderService.createOrderFromSelectedItems(result.userId(), "VNPAY", result.orderRequest());
                log.info("Order successfully created for VNPay txnRef: {}, userId: {}", result.txnRef(), result.userId());

                // Đánh dấu đã xử lý thành công (Idempotency key lưu trong 24h)
                redisService.set("payment:processed:" + result.txnRef(), "COMPLETED");
                redisService.setTimeToLive("payment:processed:" + result.txnRef(), 24, TimeUnit.HOURS);

                // Dọn dẹp dữ liệu tạm sau khi đơn hàng đã được tạo và lưu trữ bền vững
                redisServiceOrder.delete("payment:order:" + result.txnRef());
                redisServiceOrder.delete("payment:items:" + result.userId());
                redisService.delete("payment:txnRef:" + result.txnRef() + ":userId");
            }
            model.addAttribute("message", result.message());
            model.addAttribute("transactionNo", result.transactionNo());
        } else {
            log.warn("VNPay payment failed for txnRef: {}, responseCode: {}", result.txnRef(), params.get("vnp_ResponseCode"));
            model.addAttribute("message", result.message());
            model.addAttribute("responseCode", params.get("vnp_ResponseCode"));
        }

        return "payment-result.html";
    }
}