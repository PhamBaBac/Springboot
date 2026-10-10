package com.bacpham.kanban_service.configuration.security;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final GenericRedisService<String, String, String> redisService;
    private final com.bacpham.kanban_service.service.UserCacheService userCacheService;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/authenticate")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/exchange-token")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        String jwt = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        }

        if (jwt == null) {
            jwt = getJwtFromCookie(request);
        }

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null) {
                UserDetails userDetails = this.userCacheService.getUserByEmail(userEmail);

                boolean isBlacklisted = redisService.get("blacklist:" + jwt) != null;
                boolean isAccessToken = "access".equals(jwtService.extractTokenType(jwt));

                if (!isBlacklisted && isAccessToken && jwtService.isTokenValidForUser(jwt, userDetails, "access")) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        } catch (DataAccessException e) {
            // @ControllerAdvice không thể bắt exception từ Servlet Filter
            // Phải tự viết JSON response ở đây
            log.error("Database error in JWT filter (DB unavailable): {}", e.getMessage());
            sendErrorResponse(response, ErrorCode.DATABASE_CONNECTION_ERROR);
            return;
        } catch (Exception e) {
            log.error("Unexpected error in JWT filter [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            sendErrorResponse(response, ErrorCode.UNCATEGORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Viết JSON error response trực tiếp vào HttpServletResponse.
     * Cần thiết vì @ControllerAdvice / @ExceptionHandler không hoạt động trong Servlet Filter.
     */
    private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatusCode().value());
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = Map.of(
                "code", errorCode.getCode(),
                "message", errorCode.getMessage()
        );
        objectMapper.writeValue(response.getWriter(), body);
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
}