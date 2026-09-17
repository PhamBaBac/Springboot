package com.bacpham.kanban_service.service.impl;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.jsonwebtoken.Claims;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.configuration.security.JwtService;
import com.bacpham.kanban_service.dto.request.AuthenticationRequest;
import com.bacpham.kanban_service.dto.request.RegisterRequest;
import com.bacpham.kanban_service.dto.request.VerificationRequest;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.service.IAuthenticationService;
import com.bacpham.kanban_service.tfa.TwoFactorAuthenticationService;
import com.bacpham.kanban_service.utils.email.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements IAuthenticationService {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final long OTP_COOLDOWN_SECONDS = 60;

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TwoFactorAuthenticationService tfaService;
    private final EmailService emailService;
    private final GenericRedisService<String, String, String> redisService;
    @Value("${application.cookie.secure:false}")
    private boolean isCookieSecure;

    @Override
    public void register(RegisterRequest request) {
        Optional<User> existingUser = repository.findByEmail(request.getEmail());
        if (existingUser.isPresent()) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        String cooldownKey = "cooldown:email:" + request.getEmail();
        if (redisService.get(cooldownKey) != null) {
            throw new AppException(ErrorCode.RESEND_CODE_COOLDOWN);
        }

        try {
            // Serialize request vào JSON và lưu vào Redis
            String json = new ObjectMapper().writeValueAsString(request);
            redisService.set("register:" + request.getEmail(), json);
            redisService.setTimeToLive("register:" + request.getEmail(), 10, TimeUnit.MINUTES);

            // Gửi code xác thực đến email bằng SecureRandom
            String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
            emailService.sendVerificationCodeEmail(request.getEmail(), code);
            redisService.set("code:" + request.getEmail(), code);
            redisService.setTimeToLive("code:" + request.getEmail(), 5, TimeUnit.MINUTES);

            // Set cooldown 60s
            redisService.set(cooldownKey, "true");
            redisService.setTimeToLive(cooldownKey, OTP_COOLDOWN_SECONDS, TimeUnit.SECONDS);

            // Reset failed attempts count
            redisService.delete("otp_attempts:" + request.getEmail());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED);
        }
    }


    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request, HttpServletResponse response) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.isMfaEnabled()) {
            return AuthenticationResponse.builder()
                    .mfaEnabled(true)
                    .build();
        }

        String accessToken = createAndStoreAccessToken(user);
        createOrRenewRefreshToken(user, response);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .userId(user.getId())
                .mfaEnabled(false)
                .build();
    }

    @Override
    @Transactional
    public AuthenticationResponse verifyCode(VerificationRequest request, HttpServletResponse response) {
        log.info("Verification request: {}", request);
        User user = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + request.getEmail()));

        if (tfaService.isOtpNotValid(user.getSecret(), request.getCode())) {
            throw new BadCredentialsException("Code is not valid");
        }

        if (!user.isMfaEnabled()) {
            user.setMfaEnabled(true);
            repository.save(user);
        }

        String accessToken = createAndStoreAccessToken(user);
        createOrRenewRefreshToken(user, response);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .userId(user.getId())
                .mfaEnabled(true)
                .build();
    }

    @Override
    public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            writeErrorResponse(response, "Missing refresh token", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            String email = jwtService.extractUsername(refreshToken);
            boolean isRefresh = "refresh".equals(jwtService.extractTokenType(refreshToken));

            if (!isRefresh || jwtService.isTokenExpired(refreshToken)) {
                writeErrorResponse(response, "Invalid or expired refresh token", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            User user = repository.findByEmail(email)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            if (!jwtService.isTokenValidForUser(refreshToken, user, "refresh")) {
                writeErrorResponse(response, "Invalid refresh token", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            String accessToken = createAndStoreAccessToken(user);
            AuthenticationResponse authResponse = AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .mfaEnabled(user.isMfaEnabled())
                    .build();

            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            new ObjectMapper().writeValue(response.getOutputStream(), authResponse);

        } catch (ExpiredJwtException e) {
            writeErrorResponse(response, "Refresh token expired", HttpServletResponse.SC_UNAUTHORIZED);
        } catch (Exception e) {
            writeErrorResponse(response, e.getMessage(), HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = extractTokenFromHeader(request);
        if (token == null) {
            token = getJwtFromCookie(request);
        }

        if (token != null) {
            try {
                Date expiration = jwtService.extractClaim(token, Claims::getExpiration);
                long remainingMillis = expiration.getTime() - System.currentTimeMillis();

                if (remainingMillis > 0) {
                    redisService.set("blacklist:" + token, "revoked");
                    redisService.setTimeToLive("blacklist:" + token, remainingMillis, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                log.warn("Error blacklisting token on logout: {}", e.getMessage());
            }
        }

        ResponseCookie cleanCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isCookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cleanCookie.toString());
    }

    @Override
    public void sendCodeEmail(String email) throws MessagingException {
        String cooldownKey = "cooldown:email:" + email;
        if (redisService.get(cooldownKey) != null) {
            throw new AppException(ErrorCode.RESEND_CODE_COOLDOWN);
        }

        String userDataJson = redisService.get("register:" + email);
        Optional<User> user = repository.findByEmail(email);

        if (userDataJson == null && user.isEmpty()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        emailService.sendVerificationCodeEmail(email, code);

        redisService.set("code:" + email, code);
        redisService.setTimeToLive("code:" + email, 5, TimeUnit.MINUTES);

        // Set cooldown 60s
        redisService.set(cooldownKey, "true");
        redisService.setTimeToLive(cooldownKey, OTP_COOLDOWN_SECONDS, TimeUnit.SECONDS);

        // Reset attempts count for new code
        redisService.delete("otp_attempts:" + email);
    }


    @Override
    @Transactional
    public AuthenticationResponse verifyCodeEmail(VerificationRequest request, HttpServletResponse response) {
        log.info("Verifying code for email: {}", request.getEmail());

        String email = request.getEmail();
        String redisCodeKey = "code:" + email;
        String redisRegisterKey = "register:" + email;
        String redisAttemptsKey = "otp_attempts:" + email;

        // 1. Kiểm tra số lần nhập sai trước đó
        String attemptsStr = redisService.get(redisAttemptsKey);
        int attempts = (attemptsStr != null) ? Integer.parseInt(attemptsStr) : 0;
        if (attempts >= MAX_OTP_ATTEMPTS) {
            redisService.delete(redisCodeKey);
            throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
        }

        String codeInRedis = redisService.get(redisCodeKey);
        log.info("Verifying code from Redis: {}", codeInRedis);

        // 2. Kiểm tra mã OTP
        if (codeInRedis == null || !codeInRedis.equals(request.getCode())) {
            attempts++;
            redisService.set(redisAttemptsKey, String.valueOf(attempts));
            redisService.setTimeToLive(redisAttemptsKey, 15, TimeUnit.MINUTES);

            if (attempts >= MAX_OTP_ATTEMPTS) {
                redisService.delete(redisCodeKey); // Khóa và vô hiệu hóa mã OTP
                throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
            }
            throw new AppException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // 3. Mã đúng -> Dọn sạch mã, số lần sai và cooldown
        redisService.delete(redisCodeKey);
        redisService.delete(redisAttemptsKey);
        redisService.delete("cooldown:email:" + email);

        Optional<User> optionalUser = repository.findByEmail(email);

        if (optionalUser.isPresent()) {
            User existingUser = optionalUser.get();

            String accessToken = createAndStoreAccessToken(existingUser);
            createOrRenewRefreshToken(existingUser, response);

            return AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .userId(existingUser.getId())
                    .mfaEnabled(existingUser.isMfaEnabled())
                    .build();
        }

        String registerJson = redisService.get(redisRegisterKey);

        if (registerJson == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        try {
            RegisterRequest registerRequest = new ObjectMapper().readValue(registerJson, RegisterRequest.class);

            User newUser = buildUserFromRequest(registerRequest);

            if (registerRequest.isMfaEnabled()) {
                newUser.setSecret(tfaService.generateNewSecret());
            }

            User savedUser = repository.save(newUser);

            redisService.delete(redisCodeKey);
            redisService.delete(redisRegisterKey);

            String accessToken = createAndStoreAccessToken(savedUser);
            createOrRenewRefreshToken(savedUser, response);

            return AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .userId(savedUser.getId())
                    .mfaEnabled(savedUser.isMfaEnabled())
                    .build();

        } catch (IOException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED);
        }
    }

    @Override
    public AuthenticationResponse exchangeToken(String code) {
        String key = "oauth2:code:" + code;
        String authJson = redisService.get(key);

        if (authJson == null) {
            throw new AppException(ErrorCode.INVALID_EXCHANGE_CODE);
        }

        // Xóa ngay mã code sau 1 lần đổi (One-time use)
        redisService.delete(key);

        try {
            return new ObjectMapper().readValue(authJson, AuthenticationResponse.class);
        } catch (IOException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED);
        }
    }

    // =================== Helper Methods ===================

    private User buildUserFromRequest(RegisterRequest request) {
        return User.builder()
                .firstname(request.getFirstName())
                .lastname(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .mfaEnabled(request.isMfaEnabled())
                .build();
    }

    private String createAndStoreAccessToken(User user) {
        return jwtService.generateAccessToken(user);
    }

    private String createOrRenewRefreshToken(User user, HttpServletResponse response) {
        String token = jwtService.generateRefreshToken(user);

        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(isCookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(7))
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return token;
    }

    private String getJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        return Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(c -> REFRESH_TOKEN_COOKIE_NAME.equals(c.getName())
                        || "refresh_token".equalsIgnoreCase(c.getName())
                        || "refreshTokenUser".equals(c.getName())
                        || "refreshTokenAdmin".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String extractTokenFromHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7)
                : null;
    }

    private void writeErrorResponse(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
