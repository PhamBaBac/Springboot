package com.bacpham.kanban_service.configuration.ratelimit;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final GenericRedisService<String, String, String> redisService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            rateLimit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        }

        if (rateLimit == null) {
            return true;
        }

        String identifier = resolveIdentifier(request, rateLimit.type());
        String redisKey = String.format("ratelimit:%s:%s", rateLimit.prefix(), identifier);

        Long currentCount = redisService.increment(redisKey);
        if (currentCount == null) {
            return true;
        }

        // Nếu là request đầu tiên trong window, thiết lập thời gian hết hạn
        if (currentCount == 1) {
            redisService.setTimeToLive(redisKey, rateLimit.duration(), TimeUnit.SECONDS);
        }

        if (currentCount > rateLimit.limit()) {
            log.warn("Rate limit exceeded for prefix={}, identifier={}, count={}/{}",
                    rateLimit.prefix(), identifier, currentCount, rateLimit.limit());

            response.setHeader("Retry-After", String.valueOf(rateLimit.duration()));
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimit.limit()));
            response.setHeader("X-RateLimit-Remaining", "0");

            if (rateLimit.message() != null && !rateLimit.message().isBlank()) {
                throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED, rateLimit.message());
            }
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        // Header thông tin rate limit cho client
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimit.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, rateLimit.limit() - currentCount)));

        return true;
    }

    private String resolveIdentifier(HttpServletRequest request, RateLimitType type) {
        String clientIp = IpUtils.getClientIp(request);

        if (type == RateLimitType.IP) {
            return clientIp;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());

        if (type == RateLimitType.USER) {
            return isAuthenticated ? auth.getName() : clientIp;
        }

        // IP_OR_USER: ưu tiên tên user nếu đã đăng nhập, ngược lại dùng IP
        return isAuthenticated ? "user:" + auth.getName() : "ip:" + clientIp;
    }
}
