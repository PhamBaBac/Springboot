package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.configuration.security.JwtService;
import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.AuthenticationRequest;
import com.bacpham.kanban_service.dto.request.RegisterRequest;
import com.bacpham.kanban_service.dto.request.VerificationRequest;
import com.bacpham.kanban_service.dto.response.AuthenticationResponse;
import com.bacpham.kanban_service.dto.response.UserResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.UserMapper;
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

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements IAuthenticationService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final TwoFactorAuthenticationService tfaService;
    private final EmailService emailService;
    private final GenericRedisService<String, String, String> redisService;

    @Override
    public void register(RegisterRequest request) {
        Optional<User> existingUser = repository.findByEmail(request.getEmail());
        if (existingUser.isPresent()) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        try {
            // Serialize request vào JSON và lưu vào Redis
            String json = new ObjectMapper().writeValueAsString(request);
            redisService.set("register:" + request.getEmail(), json);
            redisService.setTimeToLive("register:" + request.getEmail(), 10, TimeUnit.MINUTES);

            // Gửi code xác thực đến email
            String code = String.format("%06d", (int) (Math.random() * 1_000_000));
            emailService.sendVerificationCodeEmail(request.getEmail(), code);
            redisService.set("code:" + request.getEmail(), code);
            redisService.setTimeToLive("code:" + request.getEmail(), 5, TimeUnit.MINUTES);
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

            if (!jwtService.isTokenValid(refreshToken, user, "refresh")) {
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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            String email = jwtService.extractUsername(token);
            User user = repository.findByEmail(email)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            redisService.delete("accessToken:" + user.getId());
            redisService.delete("refreshToken:" + user.getId());

            Cookie cookie = new Cookie("refreshToken", null);
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @Override
    public void sendCodeEmail(String email) throws MessagingException {
        String userDataJson = redisService.get("register:" + email);

        User user = repository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (userDataJson == null && user.getEmail() == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND); // hoặc ErrorCode.USER_NOT_REGISTERED
        }

        String code = String.format("%06d", (int) (Math.random() * 1_000_000));

        emailService.sendVerificationCodeEmail(email, code);

        redisService.set("code:" + email, code);
        redisService.setTimeToLive("code:" + email, 5, TimeUnit.MINUTES);
    }


    @Override
    @Transactional
    public AuthenticationResponse verifyCodeEmail(VerificationRequest request, HttpServletResponse response) {
        log.info("Verifying code for code: {}", request.getCode());

        String redisCodeKey = "code:" + request.getEmail();
        String redisRegisterKey = "register:" + request.getEmail();

        String codeInRedis = redisService.get(redisCodeKey);
        log.info("Verifying code from Redis: {}", codeInRedis);

        // Bước 1: Xác minh mã OTP
        if (codeInRedis == null || !codeInRedis.equals(request.getCode())) {
            throw new  AppException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // Bước 2: Tìm user trong DB
        Optional<User> optionalUser = repository.findByEmail(request.getEmail());

        // Trường hợp 1: User đã tồn tại, xác thực xong thì login luôn
        if (optionalUser.isPresent()) {
            User existingUser = optionalUser.get();

            // Xóa mã trong Redis sau khi dùng
            redisService.delete(redisCodeKey);

            String accessToken = createAndStoreAccessToken(existingUser);
            createOrRenewRefreshToken(existingUser, response);

            return AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .userId(existingUser.getId())
                    .mfaEnabled(existingUser.isMfaEnabled())
                    .build();
        }

        // Trường hợp 2: User chưa tồn tại → kiểm tra register info từ Redis
        String registerJson = redisService.get(redisRegisterKey);

        if (registerJson == null) {
            // Không có dữ liệu để đăng ký => lỗi
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        try {
            RegisterRequest registerRequest = new ObjectMapper().readValue(registerJson, RegisterRequest.class);

            User newUser = buildUserFromRequest(registerRequest);

            if (registerRequest.isMfaEnabled()) {
                newUser.setSecret(tfaService.generateNewSecret());
            }

            User savedUser = repository.save(newUser);

            // Dọn dẹp Redis sau khi tạo tài khoản thành công
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
        String token = jwtService.generateAccessToken(user);
        redisService.set("accessToken:" + user.getId(), token);
        redisService.setTimeToLive("accessToken:" + user.getId(), 1, TimeUnit.DAYS);
        return token;
    }

    private String createOrRenewRefreshToken(User user, HttpServletResponse response) {
        String token = redisService.get("refreshToken:" + user.getId());

        if (token == null) {
            token = jwtService.generateRefreshToken(user);
        }

        redisService.set("refreshToken:" + user.getId(), token);
        redisService.setTimeToLive("refreshToken:" + user.getId(), 7, TimeUnit.DAYS);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite("Lax")
                .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return token;
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        return Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(c -> c.getName().equals("refreshToken") || c.getName().equals("refresh_token"))
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
