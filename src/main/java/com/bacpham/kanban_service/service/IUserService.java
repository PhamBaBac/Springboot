package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.AdminCreateUserRequest;
import com.bacpham.kanban_service.dto.request.ChangePasswordRequest;
import com.bacpham.kanban_service.dto.request.ResetPasswordRequest;
import com.bacpham.kanban_service.dto.response.PageResponse;
import com.bacpham.kanban_service.dto.response.UserAuditLogResponse;
import com.bacpham.kanban_service.dto.response.UserResponse;
import com.bacpham.kanban_service.enums.Role;

import java.security.Principal;

/**
 * Interface cho User Service.
 * Tuan thu Dependency Inversion Principle: Controller chi phu thuoc
 * vao interface nay thay vi UserService concrete class.
 */
public interface IUserService {

    /**
     * Doi mat khau nguoi dung dang dang nhap.
     */
    void changePassword(ChangePasswordRequest request, Principal connectedUser);

    /**
     * Lay URI anh QR code cho Google Authenticator (MFA setup).
     */
    String getSecretImageUriByEmail(String email);

    /**
     * Tra ve thong tin nguoi dung theo email.
     */
    UserResponse getUserByEmail(String email);

    /**
     * Tat Two-Factor Authentication cho nguoi dung.
     */
    void disableTfaForUser(String email);

    /**
     * Dat lai mat khau nguoi dung (forgot password flow).
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Lấy danh sách tài khoản phân trang cho Admin.
     */
    PageResponse<UserResponse> getAdminUsers(String search, Role role, int page, int pageSize);

    /**
     * Admin tạo tài khoản mới và gán Role.
     */
    UserResponse adminCreateUser(AdminCreateUserRequest request, String adminEmail);

    /**
     * Admin cập nhật Role cho tài khoản.
     */
    UserResponse updateUserRole(String userId, Role newRole, String adminEmail);

    /**
     * Lấy danh sách nhật ký hoạt động (Audit Logs) phân trang.
     */
    PageResponse<UserAuditLogResponse> getAuditLogs(String search, int page, int pageSize);
}
