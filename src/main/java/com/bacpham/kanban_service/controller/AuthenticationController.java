package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.AuthenticationRequest;
import com.bacpham.kanban_service.dto.request.RegisterRequest;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;
import com.bacpham.kanban_service.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;

    @PostMapping("/register")
    public ApiResponse<AuthenticationResponse> register(
            @RequestBody RegisterRequest request
    ) {
        return ApiResponse.<AuthenticationResponse>builder()
                .result(service.register(request))
                .build();
    }
    @PostMapping("/authenticate")
    public ApiResponse<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request
    ) {
        return ApiResponse.<AuthenticationResponse>builder()
                .result(service.authenticate(request))
                .build();
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

}