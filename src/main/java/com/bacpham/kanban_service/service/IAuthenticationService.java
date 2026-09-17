package com.bacpham.kanban_service.service;

import java.io.IOException;

import com.bacpham.kanban_service.dto.request.AuthenticationRequest;
import com.bacpham.kanban_service.dto.request.RegisterRequest;
import com.bacpham.kanban_service.dto.request.VerificationRequest;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;

import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface IAuthenticationService {
    void register(RegisterRequest request);

    AuthenticationResponse authenticate(AuthenticationRequest request, HttpServletResponse response);

    void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException;

    AuthenticationResponse verifyCode(VerificationRequest verificationRequest, HttpServletResponse response);

    void sendCodeEmail(String email) throws MessagingException;


    void logout(HttpServletRequest request, HttpServletResponse response) throws IOException;

    AuthenticationResponse verifyCodeEmail(VerificationRequest request , HttpServletResponse response) throws MessagingException;

    AuthenticationResponse exchangeToken(String code);
}
