package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheService {

    private final UserRepository userRepository;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long DEFAULT_TTL_MILLIS = TimeUnit.MINUTES.toMillis(15);

    public record CacheEntry(User user, long expiryTime) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    public User getUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        CacheEntry entry = cache.get(email);
        if (entry != null && !entry.isExpired()) {
            return entry.user();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        cache.put(email, new CacheEntry(user, System.currentTimeMillis() + DEFAULT_TTL_MILLIS));
        return user;
    }

    public void evictUser(String email) {
        if (email != null) {
            cache.remove(email);
            log.debug("Evicted user from cache: {}", email);
        }
    }

    public void clear() {
        cache.clear();
    }
}
