package com.bacpham.kanban_service.service;

import java.security.Principal;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.bacpham.kanban_service.dto.request.ChangePasswordRequest;
import com.bacpham.kanban_service.dto.request.ResetPasswordRequest;
import com.bacpham.kanban_service.dto.response.UserResponse;
import com.bacpham.kanban_service.entity.User;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import com.bacpham.kanban_service.mapper.UserMapper;
import com.bacpham.kanban_service.repository.UserRepository;
import com.bacpham.kanban_service.tfa.TwoFactorAuthenticationService;
import com.bacpham.kanban_service.utils.email.EmailService;

import com.bacpham.kanban_service.configuration.redis.GenericRedisService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    @Override
    public void changePassword(ChangePasswordRequest request, Principal connectedUser) {

        var user = (User) ((UsernamePasswordAuthenticationToken) connectedUser).getPrincipal();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.WRONG_PASSWORD);
        }
        if (!request.getNewPassword().equals(request.getConfirmationPassword())) {
            throw new AppException(ErrorCode.PASSWORDS_NOT_MATCH);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        repository.save(user);
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
        log.info("secret image uri: {}", secretImageUri);

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
    }
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

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

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        repository.save(user);

        // Thu hồi mã OTP và cờ xác thực sau khi đổi mật khẩu thành công
        redisService.delete("pwd_reset_verified:" + email);
        redisService.delete("code:" + email);
    }

}