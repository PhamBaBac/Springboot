package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.*;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;
import com.bacpham.kanban_service.dto.response.UserResponse;
import com.bacpham.kanban_service.service.impl.AuthenticationServiceImpl;
import com.bacpham.kanban_service.service.impl.CartServiceImpl;
import com.bacpham.kanban_service.service.impl.RedisCartServiceImpl;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationServiceImpl service;
    private final RedisCartServiceImpl redisCartService;

    @PostMapping("/register")
    public ApiResponse<AuthenticationResponse> register(
            @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        var result = service.register(request, response);
        if(result.isMfaEnabled()){
            return ApiResponse.<AuthenticationResponse>builder()
                    .message("Registration successful, please complete 2FA setup")
                    .result(result)
                    .build();
        } else {
            return ApiResponse.<AuthenticationResponse>builder()
                    .message("Registration successful")
                    .result(result)
                    .build();
        }
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
                        .result(authResponse)
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


    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ApiResponse.<UserResponse>builder()
                    .message("User not authenticated")
                    .result(null)
                    .build();
        }

        UserResponse user = service.getUserByEmail(userDetails.getUsername()); // giả sử bạn có hàm này
        return ApiResponse.<UserResponse>builder()
                .result(user)
                .message("Get user info successfully")
                .build();
    }

    @PostMapping("/verify")
    public ApiResponse<?> sendCodeAuthenticator(
            @RequestBody VerificationRequest verificationRequest,
            HttpServletResponse response
    ){
        return ApiResponse.<AuthenticationResponse>builder()
                .message("Your message here")
                .result(service.verifyCode(verificationRequest, response))
                .build();
    }
    @PostMapping("/verify-code-email")
    public ApiResponse<?> verifyCodeEmail(@RequestBody VerificationRequest request) {
        service.verifyCodeEmail(request);
        return ApiResponse.builder()
                .message("Code is valid")
                .build();
    }


    @PutMapping("/secret-image")
    public ApiResponse<?> getSecretImage(@RequestBody EmailRequest request) {
        String secretImage = service.updateSecret(request.getEmail().trim());
        return ApiResponse.<String>builder()
                .message("Secret image retrieved successfully")
                .result(secretImage)
                .build();
    }

    @GetMapping("/user")
    public Map<String, Object> user(@AuthenticationPrincipal OAuth2User principal) {
        return Collections.singletonMap("name", principal.getAttribute("name"));
    }
    @GetMapping("/failure")
    public ApiResponse<?> fail() {
        return ApiResponse.builder()
                .code(400)
                .message("Authentication failed")
                .build();
    }


}

