package com.bacpham.kanban_service.configuration.socket;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.configuration.security.JwtService;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.service.UserCacheService;
import com.corundumstudio.socketio.AuthorizationListener;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.HandshakeData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SocketAuthorizationListener implements AuthorizationListener {

    private final JwtService jwtService;
    private final GenericRedisService<String, String, String> redisService;
    private final UserCacheService userCacheService;

    @Override
    public AuthorizationResult getAuthorizationResult(HandshakeData data) {
        String token = extractToken(data);
        if (token == null || token.isBlank()) {
            log.warn("Socket handshake rejected: Missing accessToken from address {}", data.getAddress());
            return AuthorizationResult.FAILED_AUTHORIZATION;
        }

        try {
            // 1. Kiểm tra token có trong Redis Blacklist không (đã đăng xuất)
            if (redisService.get("blacklist:" + token) != null) {
                log.warn("Socket handshake rejected: Token has been blacklisted");
                return AuthorizationResult.FAILED_AUTHORIZATION;
            }

            // 2. Validate token (signature, expiration, access type)
            if (!jwtService.validateAccessToken(token)) {
                log.warn("Socket handshake rejected: Invalid or expired access token");
                return AuthorizationResult.FAILED_AUTHORIZATION;
            }

            // 3. Trích xuất username (email) từ token
            String email = jwtService.extractUsername(token);
            if (email == null || email.isBlank()) {
                log.warn("Socket handshake rejected: Cannot extract username from token");
                return AuthorizationResult.FAILED_AUTHORIZATION;
            }

            // 4. Kiểm tra user có tồn tại và hợp lệ không
            User user = userCacheService.getUserByEmail(email);
            if (user == null) {
                log.warn("Socket handshake rejected: User not found with email {}", email);
                return AuthorizationResult.FAILED_AUTHORIZATION;
            }

            log.info("Socket handshake authorized successfully for user: {} (role={})", email, user.getRole());
            return AuthorizationResult.SUCCESSFUL_AUTHORIZATION;

        } catch (Exception e) {
            log.error("Socket handshake authorization error: {}", e.getMessage());
            return AuthorizationResult.FAILED_AUTHORIZATION;
        }
    }

    public static String extractToken(HandshakeData data) {
        if (data == null) {
            return null;
        }

        // 1. Thử param 'accessToken' (chuẩn của frontend hiện tại)
        String token = data.getSingleUrlParam("accessToken");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        // 2. Thử param 'token'
        token = data.getSingleUrlParam("token");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        // 3. Thử Header 'Authorization: Bearer ...'
        String authHeader = data.getHttpHeaders().get("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        return null;
    }
}