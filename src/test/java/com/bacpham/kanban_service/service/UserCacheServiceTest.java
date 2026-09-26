package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCacheServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserCacheService userCacheService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userCacheService = new UserCacheService(userRepository);
        testUser = User.builder()
                .email("test@example.com")
                .firstname("John")
                .lastname("Doe")
                .build();
    }

    @Test
    @DisplayName("Should query DB on first call and cache result for subsequent calls")
    void testCacheHit() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        User firstResult = userCacheService.getUserByEmail("test@example.com");
        assertNotNull(firstResult);
        assertEquals("John", firstResult.getFirstname());
        verify(userRepository, times(1)).findByEmail("test@example.com");

        User secondResult = userCacheService.getUserByEmail("test@example.com");
        assertNotNull(secondResult);
        assertSame(firstResult, secondResult);
        verify(userRepository, times(1)).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("Should query DB again after evictUser is called")
    void testCacheEviction() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        userCacheService.getUserByEmail("test@example.com");
        verify(userRepository, times(1)).findByEmail("test@example.com");

        userCacheService.evictUser("test@example.com");

        userCacheService.getUserByEmail("test@example.com");
        verify(userRepository, times(2)).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("Should throw USER_NOT_FOUND when user does not exist in DB")
    void testUserNotFound() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                userCacheService.getUserByEmail("nonexistent@example.com")
        );
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }
}
