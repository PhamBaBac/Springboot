package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ChangePasswordRequest;
import com.bacpham.kanban_service.dto.request.ResetPasswordRequest;
import com.bacpham.kanban_service.dto.request.UserActiveRequest;
import com.bacpham.kanban_service.dto.response.UserResponse;
import com.bacpham.kanban_service.service.UserActivityService;
import com.bacpham.kanban_service.service.UserService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService service;
    private final UserActivityService userActivityService;


    @PatchMapping("/changePassword")
    public ApiResponse<?> changePassword(
            @RequestBody ChangePasswordRequest request,
            Principal connectedUser
    ) {
        service.changePassword(request, connectedUser);
        return  ApiResponse.<Void>builder()
                .message("Password changed successfully")
                .build();
    }
    @GetMapping("/secretImageUri")
    public ResponseEntity<String> getSecretImageUri(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String connectedUser = userDetails.getUsername();
        log.info("Fetching secret image URI for user: {}", connectedUser);
        String secretImageUri = service.getSecretImageUriByEmail(connectedUser);
        return ResponseEntity.ok(secretImageUri);
    }
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ApiResponse.<UserResponse>builder()
                    .message("User not authenticated")
                    .result(null)
                    .build();
        }

        UserResponse user = service.getUserByEmail(userDetails.getUsername());
        log.info("Fetched user info for: {}", user.getAvatarUrl());
        return ApiResponse.<UserResponse>builder()
                .result(user)
                .message("Get user info successfully")
                .build();
    }
    @PutMapping("/disable-tfa")
    public ApiResponse<?> disableTfa(@RequestParam String email) {
        service.disableTfaForUser(email);

        return ApiResponse.builder()
                .message("Two-factor authentication disabled successfully")
                .build();
    }

    @PutMapping("/reset-password")
    public ApiResponse<?> forgotPassword(
            @RequestBody ResetPasswordRequest request
            ) {
        service.resetPassword(request);
        return ApiResponse.builder()
                .message("Forgot password request processed successfully")
                .build();
    }
    @PostMapping("/userActivity")
    public ApiResponse<?> userActivity(
            @RequestBody UserActiveRequest userActiveRequest
            ) {
        userActivityService.recordViewProductActivity(userActiveRequest);
        return ApiResponse.<Void>builder()
                .message("User activity recorded successfully")
                .build();
    }

}