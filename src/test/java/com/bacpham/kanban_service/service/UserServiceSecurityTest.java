package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import com.bacpham.kanban_service.dto.request.ResetPasswordRequest;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.tfa.TwoFactorAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceSecurityTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRepository repository;

    private FakeRedisService fakeRedisService;

    private UserService userService;

    private User sampleUser;

    static class FakeRedisService extends GenericRedisService<String, String, String> {
        private final Map<String, String> storage = new ConcurrentHashMap<>();

        public FakeRedisService() {
            super();
        }

        @Override
        public void set(String key, String value) {
            storage.put(key, value);
        }

        @Override
        public String get(String key) {
            return storage.get(key);
        }

        @Override
        public void delete(String key) {
            storage.remove(key);
        }

        @Override
        public void setTimeToLive(String key, long timeout, TimeUnit timeUnit) {
            // no-op for unit test
        }
    }

    @BeforeEach
    void setUp() {
        fakeRedisService = new FakeRedisService();

        userService = new UserService(
                passwordEncoder,
                repository,
                new TwoFactorAuthenticationService(),
                null,
                null,
                fakeRedisService
        );

        sampleUser = User.builder()
                .email("user@example.com")
                .password("encodedOldPassword")
                .build();
    }

    @Test
    @DisplayName("Reset password without OTP or verified flag should throw INVALID_VERIFICATION_CODE")
    void resetPassword_WithoutOtp_ShouldThrowException() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("user@example.com")
                .newPassword("NewSecurePass@123")
                .code(null)
                .build();

        AppException ex = assertThrows(AppException.class, () -> userService.resetPassword(request));
        assertEquals(ErrorCode.INVALID_VERIFICATION_CODE, ex.getErrorCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password with wrong OTP code should throw INVALID_VERIFICATION_CODE")
    void resetPassword_WithWrongOtp_ShouldThrowException() {
        fakeRedisService.set("code:user@example.com", "999999");

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("user@example.com")
                .newPassword("NewSecurePass@123")
                .code("111111")
                .build();

        AppException ex = assertThrows(AppException.class, () -> userService.resetPassword(request));
        assertEquals(ErrorCode.INVALID_VERIFICATION_CODE, ex.getErrorCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password with valid OTP code should succeed and clean up Redis")
    void resetPassword_WithValidOtp_ShouldSucceed() {
        fakeRedisService.set("code:user@example.com", "123456");

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("user@example.com")
                .newPassword("NewSecurePass@123")
                .code("123456")
                .build();

        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.encode("NewSecurePass@123")).thenReturn("encodedNewPassword");

        userService.resetPassword(request);

        assertEquals("encodedNewPassword", sampleUser.getPassword());
        verify(repository, times(1)).save(sampleUser);
        assertNull(fakeRedisService.get("pwd_reset_verified:user@example.com"));
        assertNull(fakeRedisService.get("code:user@example.com"));
    }

    @Test
    @DisplayName("Reset password with valid verified flag from Redis should succeed")
    void resetPassword_WithVerifiedFlag_ShouldSucceed() {
        fakeRedisService.set("pwd_reset_verified:user@example.com", "true");

        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .email("user@example.com")
                .newPassword("NewSecurePass@123")
                .build();

        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.encode("NewSecurePass@123")).thenReturn("encodedNewPassword");

        userService.resetPassword(request);

        assertEquals("encodedNewPassword", sampleUser.getPassword());
        verify(repository, times(1)).save(sampleUser);
        assertNull(fakeRedisService.get("pwd_reset_verified:user@example.com"));
    }
}
