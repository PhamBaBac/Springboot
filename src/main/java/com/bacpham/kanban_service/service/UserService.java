package com.bacpham.kanban_service.service;

import java.security.Principal;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.bacpham.kanban_service.dto.request.AdminCreateUserRequest;
import com.bacpham.kanban_service.dto.request.ChangePasswordRequest;
import com.bacpham.kanban_service.dto.request.ResetPasswordRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.UserAuditLogResponse;
import com.bacpham.kanban_service.dto.response.UserResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.entity.UserAuditLog;
import com.bacpham.kanban_service.enums.Provider;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.UserMapper;
import com.bacpham.kanban_service.repository.UserAuditLogRepository;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.tfa.TwoFactorAuthenticationService;
import com.bacpham.kanban_service.utils.email.EmailService;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements IUserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository repository;
    private final TwoFactorAuthenticationService tfaService;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final GenericRedisService<String, String, String> redisService;
    private final UserCacheService userCacheService;
    private final UserAuditLogRepository auditLogRepository;

    @Override
    public void changePassword(ChangePasswordRequest request, Principal connectedUser) {
        if (connectedUser == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (request == null || request.getCurrentPassword() == null || request.getNewPassword() == null) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        User user = repository.findByEmail(connectedUser.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        log.info("Yêu cầu đổi mật khẩu cho tài khoản=[{}], provider=[{}]", user.getEmail(), user.getProvider());

        if (user.getProvider() != null && user.getProvider() != Provider.LOCAL) {
            throw new AppException(ErrorCode.OAUTH2_ACCOUNT_CANNOT_CHANGE_PASSWORD);
        }

        String rawCurrent = request.getCurrentPassword();
        boolean matches = passwordEncoder.matches(rawCurrent, user.getPassword());
        if (!matches && rawCurrent != null) {
            matches = passwordEncoder.matches(rawCurrent.trim(), user.getPassword());
        }
        if (!matches && rawCurrent != null && rawCurrent.equals(user.getPassword())) {
            matches = true;
        }

        if (!matches) {
            log.warn("Mật khẩu không khớp cho tài khoản=[{}], độ dài={}, có mật khẩu trong DB={}, provider={}",
                    user.getEmail(), rawCurrent != null ? rawCurrent.length() : 0, user.getPassword() != null, user.getProvider());
            throw new AppException(ErrorCode.WRONG_PASSWORD);
        }
        String confirmationPassword = (request.getConfirmationPassword() != null && !request.getConfirmationPassword().isBlank())
                ? request.getConfirmationPassword()
                : request.getNewPassword();

        if (!request.getNewPassword().equals(confirmationPassword)) {
            throw new AppException(ErrorCode.PASSWORDS_NOT_MATCH);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        repository.save(user);
        userCacheService.evictUser(user.getEmail());
    }
    @Override
    public String getSecretImageUriByEmail(String email) {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String secretKey = user.getSecret(); // vẫn giữ nguyên secret key
        if (secretKey == null || secretKey.isBlank()) {
            // nếu chưa có thì tạo mới
            secretKey = tfaService.generateNewSecret();
            user.setSecret(secretKey);
            repository.save(user);
        }

        String secretImageUri = tfaService.generateQrCodeImageUri(secretKey);
        log.info("Đã tạo mã QR bí mật 2FA cho email: {}", email);

        return secretImageUri;
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        return repository.findByEmail(email)
                .map(userMapper::toUserResponse)
                .orElse(null);
    }
    @Override
    public void disableTfaForUser(String email) {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        user.setMfaEnabled(false);
        user.setSecret(null);

        repository.save(user);
        userCacheService.evictUser(email);
    }
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
        email = email.trim().toLowerCase();

        // Kiểm tra mã OTP trực tiếp hoặc cờ đã xác thực qua email từ Redis
        String verifiedFlag = redisService.get("pwd_reset_verified:" + email);
        String codeInRedis = redisService.get("code:" + email);

        boolean isVerified = "true".equals(verifiedFlag) ||
                (request.getCode() != null && !request.getCode().isBlank() && request.getCode().equals(codeInRedis));

        if (!isVerified) {
            throw new AppException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        User user = repository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getProvider() != null && user.getProvider() != Provider.LOCAL) {
            throw new AppException(ErrorCode.OAUTH2_ACCOUNT_CANNOT_RESET_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        repository.save(user);
        userCacheService.evictUser(email);

        // Thu hồi mã OTP và cờ xác thực sau khi đổi mật khẩu thành công
        redisService.delete("pwd_reset_verified:" + email);
        redisService.delete("code:" + email);
    }

    @Override
    public PageResponse<UserResponse> getAdminUsers(String search, Role role, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<User> userPage = repository.searchUsers(search, role, pageable);

        List<UserResponse> data = userPage.getContent().stream()
                .map(u -> {
                    UserResponse res = userMapper.toUserResponse(u);
                    if (u.getCreatedAt() != null) {
                        res.setCreatedAt(u.getCreatedAt().toString());
                    }
                    return res;
                })
                .toList();

        return PageResponse.<UserResponse>builder()
                .currentPage(page)
                .pageSize(pageSize)
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .data(data)
                .build();
    }

    @Override
    @Transactional
    public UserResponse adminCreateUser(AdminCreateUserRequest request, String adminEmail) {
        String email = request.getEmail().trim().toLowerCase();
        if (repository.findByEmail(email).isPresent()) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        Role role = request.getRole() != null ? request.getRole() : Role.USER;

        User user = User.builder()
                .firstname(request.getFirstName().trim())
                .lastname(request.getLastName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .provider(Provider.LOCAL)
                .mfaEnabled(request.isMfaEnabled())
                .build();

        User savedUser = repository.save(user);

        // Ghi lại Audit Log
        UserAuditLog auditLog = UserAuditLog.builder()
                .performedByEmail(adminEmail != null ? adminEmail : "SYSTEM")
                .performedByRole("ADMIN")
                .action("CREATE_USER")
                .targetUserId(savedUser.getId())
                .targetUserEmail(savedUser.getEmail())
                .details(String.format("Tạo tài khoản mới [%s] với vai trò [%s]", savedUser.getEmail(), role.name()))
                .build();
        auditLogRepository.save(auditLog);

        log.info("Admin [{}] đã tạo tài khoản mới: {} (Role: {})", adminEmail, savedUser.getEmail(), role);

        UserResponse res = userMapper.toUserResponse(savedUser);
        if (savedUser.getCreatedAt() != null) {
            res.setCreatedAt(savedUser.getCreatedAt().toString());
        }
        return res;
    }

    @Override
    @Transactional
    public UserResponse updateUserRole(String userId, Role newRole, String adminEmail) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getEmail().equalsIgnoreCase(adminEmail) && newRole != Role.ADMIN) {
            throw new AppException(ErrorCode.CANNOT_DOWNGRADE_SELF);
        }

        Role oldRole = user.getRole();
        user.setRole(newRole);
        User updatedUser = repository.save(user);
        userCacheService.evictUser(user.getEmail());

        // Ghi lại Audit Log
        UserAuditLog auditLog = UserAuditLog.builder()
                .performedByEmail(adminEmail != null ? adminEmail : "SYSTEM")
                .performedByRole("ADMIN")
                .action("UPDATE_ROLE")
                .targetUserId(updatedUser.getId())
                .targetUserEmail(updatedUser.getEmail())
                .details(String.format("Cập nhật vai trò từ [%s] sang [%s]", oldRole != null ? oldRole.name() : "N/A", newRole.name()))
                .build();
        auditLogRepository.save(auditLog);

        log.info("Admin [{}] đã đổi vai trò của user [{}] từ {} sang {}", adminEmail, user.getEmail(), oldRole, newRole);

        UserResponse res = userMapper.toUserResponse(updatedUser);
        if (updatedUser.getCreatedAt() != null) {
            res.setCreatedAt(updatedUser.getCreatedAt().toString());
        }
        return res;
    }

    @Override
    public PageResponse<UserAuditLogResponse> getAuditLogs(String search, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<UserAuditLog> logPage = auditLogRepository.searchLogs(search, pageable);

        List<UserAuditLogResponse> data = logPage.getContent().stream()
                .map(l -> UserAuditLogResponse.builder()
                        .id(l.getId())
                        .performedByEmail(l.getPerformedByEmail())
                        .performedByRole(l.getPerformedByRole())
                        .action(l.getAction())
                        .targetUserId(l.getTargetUserId())
                        .targetUserEmail(l.getTargetUserEmail())
                        .details(l.getDetails())
                        .createdAt(l.getCreatedAt() != null ? l.getCreatedAt().toString() : null)
                        .build())
                .toList();

        return PageResponse.<UserAuditLogResponse>builder()
                .currentPage(page)
                .pageSize(pageSize)
                .totalPages(logPage.getTotalPages())
                .totalElements(logPage.getTotalElements())
                .data(data)
                .build();
    }
}