package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.configuration.JwtService;
import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.AuthenticationRequest;
import com.bacpham.kanban_service.dto.request.RegisterRequest;
import com.bacpham.kanban_service.dto.request.VerificationRequest;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;
import com.bacpham.kanban_service.dto.response.UserResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.mapper.UserMapper;
import com.bacpham.kanban_service.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;

    @PostMapping("/register")
    public ApiResponse<AuthenticationResponse> register(
            @RequestBody RegisterRequest request
    ) {
        var response = service.register(request);
        if(response.isMfaEnabled()){
            return ApiResponse.<AuthenticationResponse>builder()
                    .message("Registration successful, please complete 2FA setup")
                    .result(response)
                    .build();
        } else {
            return ApiResponse.<AuthenticationResponse>builder()
                    .message("Registration successful")
                    .result(response)
                    .build();
        }
    }
    @PostMapping("/authenticate")
    public ResponseEntity<ApiResponse<?>> authenticate(
            @RequestBody AuthenticationRequest request,
            HttpServletResponse response
    ) {
        AuthenticationResponse authResponse = service.authenticate(request);


        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh_token", authResponse.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(60 * 60 * 24 * 7) // 7 ngày
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

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
    public ApiResponse<?> logout() {
        return ApiResponse.<Void>builder()
                .message("Logout successful")
                .build();
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
    public ApiResponse<?> verifyToken(
            @RequestBody VerificationRequest verificationRequest
    ){
        return ApiResponse.<AuthenticationResponse>builder()
                .message("Your message here")
                .result(service.verifyCode(verificationRequest))
                .build();
    }


}

