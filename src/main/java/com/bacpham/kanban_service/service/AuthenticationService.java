package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.configuration.JwtService;
import com.bacpham.kanban_service.dto.request.AuthenticationRequest;
import com.bacpham.kanban_service.dto.request.RegisterRequest;
import com.bacpham.kanban_service.dto.request.VerificationRequest;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;
import com.bacpham.kanban_service.dto.response.UserResponse;
import com.bacpham.kanban_service.entity.Token;
import com.bacpham.kanban_service.enums.TokenType;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.UserMapper;
import com.bacpham.kanban_service.repository.TokenRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.tfa.TwoFactorAuthenticationService;
import com.bacpham.kanban_service.utils.email.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import java.util.Arrays;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository repository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final TwoFactorAuthenticationService tfaService;
    private final EmailService emailService;


    public AuthenticationResponse register(RegisterRequest request) {

        var user = User.builder()
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .mfaEnabled(request.isMfaEnabled())
                .build();
        if(request.isMfaEnabled()) {
            user.setSecret(tfaService.generateNewSecret());
        }
        var savedUser = repository.save(user);
        var jwtToken = jwtService.generateAccessToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);
        saveUserToken(savedUser, jwtToken);
        return AuthenticationResponse.builder()
                .secretImageUri(tfaService.generateQrCodeImageUri(user.getSecret()))
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .mfaEnabled(user.isMfaEnabled())
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var user = repository.findByEmail(request.getEmail())
                .orElseThrow();
        if(user.isMfaEnabled()) {
            return AuthenticationResponse.builder()
                    .accessToken("")
                    .refreshToken("")
                    .mfaEnabled(true)
                    .build();
        }
        var jwtToken = jwtService.generateAccessToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);
        revokeAllUserTokens(user);
        saveUserToken(user, jwtToken);
        return AuthenticationResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .mfaEnabled(false)
                .build();
    }

    private void saveUserToken(User user, String jwtToken) {
        var token = Token.builder()
                .user(user)
                .token(jwtToken)
                .tokenType(TokenType.BEARER)
                .expired(false)
                .revoked(false)
                .build();
        tokenRepository.save(token);
    }

    private void revokeAllUserTokens(User user) {
        var validUserTokens = tokenRepository.findAllValidTokenByUser(user.getId());
        if (validUserTokens.isEmpty())
            return;
        validUserTokens.forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
        });
        tokenRepository.saveAll(validUserTokens);
    }

  public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String userEmail = null;
    String refreshToken = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
            .filter(c -> c.getName().equals("refresh_token"))
            .findFirst()
            .map(Cookie::getValue)
            .orElse(null);
    log.info("refreshToken: {}", refreshToken);

    if (refreshToken == null) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"error\": \"Missing refresh token\"}");
        return;
    }

    try {
        userEmail = jwtService.extractUsername(refreshToken);
        log.info("userEmail: {}", userEmail);   
        
        boolean isRefreshToken = jwtService.extractTokenType(refreshToken).equals("refresh");
        if (!isRefreshToken) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid token type\"}");
            return;
        }

        var user = repository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (jwtService.isTokenValid(refreshToken, user, "refresh")) {
            var accessToken = jwtService.generateAccessToken(user);
            
            revokeAllUserTokens(user);
            
            saveUserToken(user, accessToken);

            var newRefreshToken = jwtService.generateRefreshToken(user);

            var authResponse = AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(newRefreshToken)
                    .mfaEnabled(false)
                    .build();

            ResponseCookie cookie = ResponseCookie.from("refresh_token", newRefreshToken)
                    .httpOnly(true)
                    .secure(false)  // Set to true in production with HTTPS
                    .path("/")
                    .maxAge(Duration.ofDays(7))  // 7 days
                    .sameSite("None")  // Required for cross-site requests
                    .build();

            response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            
            // Write response
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            new ObjectMapper().writeValue(response.getOutputStream(), authResponse);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid refresh token\"}");
        }
    } catch (Exception e) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
    }
}
    public UserResponse getUserByEmail(String email) {
        return repository.findByEmail(email)
                .map(userMapper::toUserResponse)
                .orElse(null);
    }

    public AuthenticationResponse verifyCode(VerificationRequest verificationRequest) {
        User user = repository.findByEmail(verificationRequest.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException(String.format("User %s not found", verificationRequest.getEmail())));

        if(tfaService.isOtpNotValid(user.getSecret(), verificationRequest.getCode())) {
            throw new BadCredentialsException("Code is not valid");
        }

        var jwtToken = jwtService.generateAccessToken(user);

        return AuthenticationResponse.builder()
                .accessToken(jwtToken)
                .mfaEnabled(user.isMfaEnabled())
                .build();

    }
    public String sendCodeEmail(String email) throws MessagingException {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if(!user.isMfaEnabled()){
           throw new AppException(ErrorCode.TFA_NOT_ENABLED);
        }

        var code = String.format("%06d", (int) (Math.random() * 1000000));

        emailService.sendVerificationCodeEmail(email, code);

        return code;
    }
    public String updateSecret(String email) {
        log.info("email: {}", email);
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if(!user.isMfaEnabled()){
            throw new AppException(ErrorCode.TFA_NOT_ENABLED);
        }

        String newSecret = tfaService.generateNewSecret();
        user.setSecret(newSecret);
        repository.save(user);

        return tfaService.generateQrCodeImageUri(newSecret);
    }
}

