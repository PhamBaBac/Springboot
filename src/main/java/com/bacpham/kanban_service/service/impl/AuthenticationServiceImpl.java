package com.bacpham.kanban_service.service.impl;

import com.bacpham.kanban_service.configuration.JwtService;
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
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.http.MediaType;
import java.util.Arrays;
import java.util.Optional;


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

    public AuthenticationResponse register(RegisterRequest request , HttpServletResponse response) {

        var user = User.builder()
                .firstname(request.getFirstName())
                .lastname(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .mfaEnabled(request.isMfaEnabled())
                .build();
        if(request.isMfaEnabled()) {
            user.setSecret(tfaService.generateNewSecret());
        }
        var savedUser = repository.save(user);
        var accessToken = jwtService.generateAccessToken(user);
        redisService.set("accessToken:" + savedUser.getId(), accessToken);
        redisService.setTimeToLive("accessToken:" + savedUser.getId(),1, TimeUnit.DAYS);

        var refreshToken = jwtService.generateRefreshToken(user);
        redisService.set("refreshToken:" + savedUser.getId(), refreshToken);
        redisService.setTimeToLive("refreshToken:" + savedUser.getId(),7, TimeUnit.DAYS);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(Duration.ofDays(7)) // 7 days
                .sameSite("Lax")
                .build();
         response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("User registered: {}", savedUser.getEmail());
        if (savedUser.getRole() == Role.USER) {
            return AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .userId(savedUser.getId())
                    .build();
        }
        return AuthenticationResponse.builder()
                .secretImageUri(tfaService.generateQrCodeImageUri(user.getSecret()))
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .mfaEnabled(user.isMfaEnabled())
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request, HttpServletResponse response) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        var user = repository.findByEmail(request.getEmail())
                .orElseThrow();
        if (user.isMfaEnabled()) {
            return AuthenticationResponse.builder()
                    .accessToken("")
                    .refreshToken("")
                    .mfaEnabled(true)
                    .build();
        }


        String accessToken = jwtService.generateAccessToken(user);
        redisService.set("accessToken:" + user.getId(), accessToken);
        redisService.setTimeToLive("accessToken:" + user.getId(), 1, TimeUnit.DAYS);

        String refreshTokenOld = redisService.get("refreshToken:" + user.getId());
        if (refreshTokenOld == null) {
            String refreshToken = jwtService.generateRefreshToken(user);
            redisService.set("refreshToken:" + user.getId(), refreshToken);
            redisService.setTimeToLive("refreshToken:" + user.getId(), 7, TimeUnit.DAYS);

            ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .maxAge(Duration.ofDays(7)) // 7 days
                    .sameSite("Lax")
                    .build();

            response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        } else {
            redisService.set("refreshToken:" + user.getId(), refreshTokenOld);
            redisService.setTimeToLive("refreshToken:" + user.getId(), 7, TimeUnit.DAYS);
        }

        if(user.getRole() == Role.USER) {
            return AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .userId(user.getId())
                    .build();
        }

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .mfaEnabled(false)
                .build();
    }


    public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String userEmail = null;
        String refreshToken = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(c -> c.getName().equals("refreshToken") || c.getName().equals("refresh_token"))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);

        if (refreshToken == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Missing refresh token\"}");
            return;
        }

        try {
            userEmail = jwtService.extractUsername(refreshToken);
            boolean isRefreshToken = jwtService.extractTokenType(refreshToken).equals("refresh");

            if (!isRefreshToken) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Invalid token type\"}");
                return;
            }

            var user = repository.findByEmail(userEmail)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            if (!jwtService.isTokenValid(refreshToken, user, "refresh")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Invalid refresh token\"}");
                return;
            }

            // Nếu token đã hết hạn thì từ chối luôn
            if (jwtService.isTokenExpired(refreshToken)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Refresh token expired\"}");
                return;
            }

            // Hợp lệ, tạo access token mới
            var accessToken = jwtService.generateAccessToken(user);
            redisService.set("accessToken:" + user.getId(), accessToken);
            redisService.setTimeToLive("accessToken:" + user.getId(), 2, TimeUnit.MINUTES);

            var authResponse = AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .mfaEnabled(user.isMfaEnabled())
                    .build();

            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            new ObjectMapper().writeValue(response.getOutputStream(), authResponse);

        } catch (ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Refresh token expired\"}");
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

    public AuthenticationResponse verifyCode(VerificationRequest verificationRequest, HttpServletResponse response) {
        User user = repository.findByEmail(verificationRequest.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException(String.format("User %s not found", verificationRequest.getEmail())));

        if(tfaService.isOtpNotValid(user.getSecret(), verificationRequest.getCode())) {
            throw new BadCredentialsException("Code is not valid");
        }

        String accessToken = jwtService.generateAccessToken(user);
        redisService.set("accessToken:" + user.getId(), accessToken);
        redisService.setTimeToLive("accessToken:" + user.getId(), 1, TimeUnit.DAYS);

        String refreshTokenOld = redisService.get("refreshToken:" + user.getId());
        if (refreshTokenOld == null) {
            String refreshToken = jwtService.generateRefreshToken(user);
            redisService.set("refreshToken:" + user.getId(), refreshToken);
            redisService.setTimeToLive("refreshToken:" + user.getId(), 7, TimeUnit.DAYS);

            ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .maxAge(Duration.ofDays(7)) // 7 days
                    .sameSite("Lax")
                    .build();

            response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        } else {
            redisService.set("refreshToken:" + user.getId(), refreshTokenOld);
            redisService.setTimeToLive("refreshToken:" + user.getId(), 7, TimeUnit.DAYS);
        }



        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .mfaEnabled(user.isMfaEnabled())
                .build();

    }
    public void sendCodeEmail(String email) throws MessagingException {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        var code = String.format("%06d", (int) (Math.random() * 1000000));

        emailService.sendVerificationCodeEmail(email, code);

        redisService.set("code" + user.getId(), code);
        redisService.setTimeToLive("code" + user.getId(), 5, TimeUnit.MINUTES);

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

    public void logout(HttpServletRequest request, HttpServletResponse response)  throws IOException {
        String userEmail = null;
        String accessToken = request.getHeader("Authorization");
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            accessToken = accessToken.substring(7);
        }


        if (accessToken == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            userEmail = jwtService.extractUsername(accessToken);
            var user = repository.findByEmail(userEmail)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            // Xóa token khỏi Redis
            redisService.delete("accessToken:" + user.getId());
            redisService.delete("refreshToken:" + user.getId());

            // Xóa cookie
            Cookie cookie = new Cookie("refreshToken", null);
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    public void verifyCodeEmail(VerificationRequest request) {
        User user = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String storedCode = redisService.get("code" + user.getId());
        if (storedCode == null || !storedCode.equals(request.getCode())) {
            throw new BadCredentialsException("Invalid verification code");
        }

        // Xóa mã đã sử dụng
        redisService.delete("code" + user.getId());
    }
}

