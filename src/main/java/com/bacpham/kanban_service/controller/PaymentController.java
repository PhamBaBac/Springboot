package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.OrderCreateRequest;
import com.bacpham.kanban_service.dto.response.PaymentResponse;
import com.bacpham.kanban_service.enums.PaymentType;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.service.IOrderService;
import com.bacpham.kanban_service.strategy.payment.PaymentCallbackResult;
import com.bacpham.kanban_service.strategy.payment.PaymentStrategy;
import com.bacpham.kanban_service.strategy.payment.PaymentStrategyRegistry;
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
 * PaymentController áp dụng Strategy Pattern & Registry Pattern:
 * - Tuân thủ Open/Closed Principle (OCP): Có thể mở rộng thêm MoMo, ZaloPay, Stripe mà không cần sửa Controller.
 * - Tuân thủ Dependency Inversion Principle (DIP): Phụ thuộc vào PaymentStrategy & PaymentStrategyRegistry.
 * - Tuân thủ Single Responsibility Principle (SRP):
 *     + Xử lý HTTP request/response & điều hướng thanh toán.
 *     + Tái sử dụng quy trình hậu xử lý callback (idempotency, tạo order, dọn dẹp Redis) cho tất cả các cổng.
 */
@Slf4j
@Controller
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final UserRepository userRepository;
    private final IOrderService orderService;
    private final PaymentStrategyRegistry paymentStrategyRegistry;
    private final GenericRedisService<String, String, OrderCreateRequest> redisServiceOrder;
    private final GenericRedisService<String, String, String> redisService;

    @PostMapping("/create")
    @ResponseBody
    public ApiResponse<PaymentResponse> createPayment(
            @RequestBody OrderCreateRequest request,
            @RequestParam(value = "type", required = false) String typeParam,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String paymentTypeStr = typeParam != null ? typeParam : request.getPaymentType();
        PaymentStrategy strategy = paymentStrategyRegistry.getStrategyOrDefault(paymentTypeStr);

        PaymentResponse paymentResponse = strategy.createPaymentUrl(request, user.getId(), httpRequest);

        return ApiResponse.<PaymentResponse>builder()
                .code(200)
                .message("Tạo đường dẫn thanh toán thành công")
                .data(paymentResponse)
                .build();
    }

    /**
     * Endpoint callback cho VNPay (giữ nguyên tương thích ngược 100%).
     */
    @GetMapping("/vnpay-return")
    public String vnpayReturn(@RequestParam Map<String, String> params, Model model) {
        return processCallback(paymentStrategyRegistry.getStrategy(PaymentType.VNPAY), params, model);
    }

    /**
     * Endpoint callback cho MoMo.
     */
    @GetMapping("/momo-return")
    public String momoReturn(@RequestParam Map<String, String> params, Model model) {
        return processCallback(paymentStrategyRegistry.getStrategy(PaymentType.MOMO), params, model);
    }

    /**
     * Endpoint callback động cho các cổng thanh toán tương lai (/callback/vnpay, /callback/momo, /callback/zalopay...).
     */
    @GetMapping("/callback/{gateway}")
    public String dynamicCallback(
            @PathVariable String gateway,
            @RequestParam Map<String, String> params,
            Model model) {
        PaymentStrategy strategy = paymentStrategyRegistry.getStrategyOrDefault(gateway);
        return processCallback(strategy, params, model);
    }

    /**
     * Quy trình hậu xử lý callback dùng chung cho tất cả các cổng thanh toán.
     */
    private String processCallback(PaymentStrategy strategy, Map<String, String> params, Model model) {
        PaymentType paymentType = strategy.getPaymentType();
        log.info("Nhận callback thanh toán {} với tham số: {}", paymentType, params);

        PaymentCallbackResult result = strategy.handleCallback(params);

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
            // Idempotency & Concurrency check: Tránh duplicate order khi người dùng F5 hoặc callback kép
            if (result.alreadyProcessed()) {
                log.info("Giao dịch {} cho mã tham chiếu {} đã được xử lý trước đó", paymentType, result.txnRef());
                model.addAttribute("message", "Đơn hàng đã được ghi nhận và tạo thành công trước đó.");
                model.addAttribute("transactionNo", result.transactionNo());
                return "payment-result.html";
            }

            // Tạo đơn hàng an toàn từ payload đã được validate bởi Strategy
            if (result.orderRequest() != null && result.userId() != null) {
                orderService.createOrderFromSelectedItems(result.userId(), paymentType.name(), result.orderRequest());
                log.info("Đã tạo đơn hàng thành công cho cổng thanh toán {}, mã giao dịch: {}, người dùng: {}", paymentType, result.txnRef(), result.userId());

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
            log.warn("Thanh toán {} thất bại cho mã giao dịch: {}", paymentType, result.txnRef());
            model.addAttribute("message", result.message());
            String responseCode = params.getOrDefault("vnp_ResponseCode", params.getOrDefault("resultCode", "FAILED"));
            model.addAttribute("responseCode", responseCode);
        }

        return "payment-result.html";
    }
}