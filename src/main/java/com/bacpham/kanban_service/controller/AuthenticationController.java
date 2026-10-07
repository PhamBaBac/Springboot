package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.configuration.ratelimit.RateLimit;
import com.bacpham.kanban_service.dto.request.*;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;
import com.bacpham.kanban_service.service.IAuthenticationService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bacpham.kanban_service.service.IRedisCartService;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final IAuthenticationService service;
    private final IRedisCartService redisCartService;

    @PostMapping("/register")
    @RateLimit(limit = 5, duration = 60, prefix = "auth_register", message = "Bạn đã thử đăng ký quá nhiều lần. Vui lòng thử lại sau ít phút.")
    public ApiResponse<?> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        service.register(request);
        return ApiResponse.builder()
                .message("Đã nhận thông tin đăng ký. Vui lòng xác thực email của bạn.")
                .data(true)
                .build();
    }

    @PostMapping("/send-code-email")
    @RateLimit(limit = 3, duration = 60, prefix = "auth_send_code", message = "Bạn đã gửi yêu cầu nhận mã quá nhiều lần. Vui lòng chờ 1 phút.")
    public ApiResponse<?> sendCodeEmail(@RequestBody SendCodeRequest request) throws MessagingException {
        service.sendCodeEmail(request);
        return ApiResponse.builder()
                .message("Gửi mã xác thực thành công")
                .build();
    }

    @PostMapping("/authenticate")
    @RateLimit(limit = 10, duration = 60, prefix = "auth_login", message = "Quá nhiều yêu cầu đăng nhập từ thiết bị của bạn. Vui lòng thử lại sau ít phút.")
    public ResponseEntity<ApiResponse<?>> authenticate(
            @RequestBody AuthenticationRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            HttpServletResponse response
    ) {

        AuthenticationResponse authResponse = service.authenticate(request, response);
        if (sessionId != null) {
            redisCartService.syncToDatabase(sessionId, authResponse.getUserId());
        }
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .message("Đăng nhập thành công")
                        .data(authResponse)
                        .build()
        );
    }

    @PostMapping("/exchange-token")
    public ResponseEntity<ApiResponse<?>> exchangeToken(
            @Valid @RequestBody ExchangeTokenRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        AuthenticationResponse authResponse = service.exchangeToken(request.getCode());
        if (sessionId != null && authResponse.getUserId() != null && !authResponse.isMfaEnabled()) {
            redisCartService.syncToDatabase(sessionId, authResponse.getUserId());
        }
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .message("Chuyển đổi token thành công")
                        .data(authResponse)
                        .build()
        );
    }

    @PostMapping("/refresh-token")
    public void refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        service.refreshToken(request, response);
    }


    @PostMapping("/logout")
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        service.logout(request, response);
    }

    @PostMapping("/enable-tfa")
    public ApiResponse<?> sendCodeAuthenticator(
            @RequestBody VerificationRequest verificationRequest,
            HttpServletResponse response
    ){
        return ApiResponse.<AuthenticationResponse>builder()
                .message("Kích hoạt xác thực hai yếu tố thành công")
                .data(service.verifyCode(verificationRequest, response))
                .build();
    }
    @PostMapping("/verify-code-email")
    @RateLimit(limit = 10, duration = 60, prefix = "auth_verify_otp", message = "Bạn đã nhập mã xác thực quá nhiều lần. Vui lòng thử lại sau.")
    public ApiResponse<?> verifyCodeEmail(
            @RequestBody VerificationRequest request,
            HttpServletResponse response
    ) throws MessagingException {
        AuthenticationResponse result = service.verifyCodeEmail(request, response);
        return ApiResponse.builder()
                .message("Xác thực email và kích hoạt tài khoản thành công")
                .data(result)
                .build();
    }

}

