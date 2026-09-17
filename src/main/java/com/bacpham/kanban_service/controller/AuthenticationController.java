package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.*;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;
import com.bacpham.kanban_service.service.IAuthenticationService;
import com.bacpham.kanban_service.service.impl.RedisCartServiceImpl;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final IAuthenticationService service;
    private final RedisCartServiceImpl redisCartService;

    @PostMapping("/register")
    public ApiResponse<?> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        service.register(request);
        return ApiResponse.builder()
                .message("Register info received. Please verify your email.")
                .data(true)
                .build();
    }

    @PostMapping("/send-code-email")
    public ApiResponse<?> sendCodeEmail(@RequestBody SendCodeRequest request) throws MessagingException {
        service.sendCodeEmail(request.getEmail());
        return ApiResponse.builder()
                .message("Verification code sent successfully")
                .build();
    }

    @PostMapping("/authenticate")
    public ResponseEntity<ApiResponse<?>> authenticate(
            @RequestBody AuthenticationRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            HttpServletResponse response
    ) {

        AuthenticationResponse authResponse = service.authenticate(request, response); //
        if (sessionId != null) {
            redisCartService.syncToDatabase(sessionId, authResponse.getUserId());
        }
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .message("Authentication successful")
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
        if (sessionId != null) {
            redisCartService.syncToDatabase(sessionId, authResponse.getUserId());
        }
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .message("Token exchange successful")
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
                .message("Your message here")
                .data(service.verifyCode(verificationRequest, response))
                .build();
    }
    @PostMapping("/verify-code-email")
    public ApiResponse<?> verifyCodeEmail(
            @RequestBody VerificationRequest request,
            HttpServletResponse response
    ) throws MessagingException {
        AuthenticationResponse result = service.verifyCodeEmail(request, response);
        return ApiResponse.builder()
                .message("Email verified and account activated")
                .data(result)
                .build();
    }


//    @GetMapping("/user")
//    public Map<String, Object> user(@AuthenticationPrincipal OAuth2User principal) {
//        return Collections.singletonMap("name", principal.getAttribute("name"));
//    }
    @GetMapping("/failure")
    public ApiResponse<?> fail() {
        return ApiResponse.builder()
                .code(400)
                .message("Authentication failed")
                .build();
    }


}

