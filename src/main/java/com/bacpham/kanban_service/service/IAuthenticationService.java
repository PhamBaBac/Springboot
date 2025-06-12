package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.AuthenticationRequest;
import com.bacpham.kanban_service.dto.request.RegisterRequest;
import com.bacpham.kanban_service.dto.request.VerificationRequest;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;
import com.bacpham.kanban_service.dto.response.UserResponse;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface IAuthenticationService {
    AuthenticationResponse register(RegisterRequest request, HttpServletResponse response);

    AuthenticationResponse authenticate(AuthenticationRequest request, HttpServletResponse response);

    void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException;

    UserResponse getUserByEmail(String email);

    AuthenticationResponse verifyCode(VerificationRequest verificationRequest, HttpServletResponse response);

    void sendCodeEmail(String email) throws MessagingException;

    String updateSecret(String email);

    void logout(HttpServletRequest request, HttpServletResponse response) throws IOException;

    void verifyCodeEmail(VerificationRequest request);
}
