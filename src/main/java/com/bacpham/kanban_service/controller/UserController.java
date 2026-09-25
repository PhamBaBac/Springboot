package com.bacpham.kanban_service.controller;

import com.bacpham.kanban_service.dto.request.AdminCreateUserRequest;
import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.bacpham.kanban_service.dto.request.ChangePasswordRequest;
import com.bacpham.kanban_service.dto.request.ResetPasswordRequest;
import com.bacpham.kanban_service.dto.request.UpdateUserRoleRequest;
import com.bacpham.kanban_service.dto.request.UserActiveRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.UserAuditLogResponse;
import com.bacpham.kanban_service.dto.response.UserResponse;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.service.IUserService;
import com.bacpham.kanban_service.service.UserActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bacpham.kanban_service.helper.exception.AppException;
import com.bacpham.kanban_service.helper.exception.ErrorCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final IUserService service;
    private final UserActivityService userActivityService;


    @PatchMapping("/changePassword")
    public ApiResponse<?> changePassword(
            @RequestBody ChangePasswordRequest request,
            Principal connectedUser
    ) {
        service.changePassword(request, connectedUser);
        return ApiResponse.<Void>builder()
                .message("Đổi mật khẩu thành công")
                .build();
    }
    @GetMapping("/secretImageUri")
    public ApiResponse<String> getSecretImageUri(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String connectedUser = userDetails.getUsername();
        log.info("Đang lấy mã QR xác thực 2FA cho người dùng: {}", connectedUser);
        String secretImageUri = service.getSecretImageUriByEmail(connectedUser);
        return ApiResponse.<String>builder()
                .data(secretImageUri)
                .message("Lấy mã QR 2FA thành công")
                .build();
    }
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("Lấy thông tin người dùng: {}", userDetails != null ? userDetails.getUsername() : "null");
        if (userDetails == null) {
            return ApiResponse.<UserResponse>builder()
                    .message("Người dùng chưa được xác thực")
                    .data(null)
                    .build();
        }

        UserResponse user = service.getUserByEmail(userDetails.getUsername());
        log.info("Lấy thông tin người dùng thành công: {}", user.getEmail());
        return ApiResponse.<UserResponse>builder()
                .data(user)
                .message("Lấy thông tin người dùng thành công")
                .build();
    }
    @PutMapping("/disable-tfa")
    public ApiResponse<?> disableTfa(
            @RequestParam String email,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!userDetails.getUsername().equals(email) && !isAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        service.disableTfaForUser(email);

        return ApiResponse.builder()
                .message("Đã tắt xác thực hai yếu tố thành công")
                .build();
    }

    @PutMapping("/reset-password")
    public ApiResponse<?> forgotPassword(
            @RequestBody ResetPasswordRequest request
            ) {
        service.resetPassword(request);
        return ApiResponse.builder()
                .message("Yêu cầu đặt lại mật khẩu đã được xử lý thành công")
                .build();
    }
    @PostMapping("/userActivity")
    public ApiResponse<?> userActivity(
            @RequestBody UserActiveRequest userActiveRequest
            ) {
        userActivityService.recordViewProductActivity(userActiveRequest);
        return ApiResponse.<Void>builder()
                .message("Ghi nhận hoạt động người dùng thành công")
                .build();
    }

    @GetMapping("/admin/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserResponse>> getAdminUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PageResponse<UserResponse> result = service.getAdminUsers(search, role, page, pageSize);
        return ApiResponse.<PageResponse<UserResponse>>builder()
                .data(result)
                .message("Lấy danh sách người dùng thành công")
                .build();
    }

    @PostMapping("/admin/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> adminCreateUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String adminEmail = userDetails != null ? userDetails.getUsername() : "ADMIN";
        UserResponse user = service.adminCreateUser(request, adminEmail);
        return ApiResponse.<UserResponse>builder()
                .data(user)
                .message("Tạo tài khoản và phân quyền thành công")
                .build();
    }

    @PatchMapping("/admin/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> updateUserRole(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String adminEmail = userDetails != null ? userDetails.getUsername() : "ADMIN";
        UserResponse user = service.updateUserRole(userId, request.getRole(), adminEmail);
        return ApiResponse.<UserResponse>builder()
                .data(user)
                .message("Cập nhật vai trò người dùng thành công")
                .build();
    }

    @GetMapping("/admin/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserAuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PageResponse<UserAuditLogResponse> logs = service.getAuditLogs(search, page, pageSize);
        return ApiResponse.<PageResponse<UserAuditLogResponse>>builder()
                .data(logs)
                .message("Lấy nhật ký hoạt động hệ thống thành công")
                .build();
    }
}