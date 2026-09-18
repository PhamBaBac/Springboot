package com.bacpham.kanban_service.service;

import com.bacpham.kanban_service.dto.request.ChangePasswordRequest;
import com.bacpham.kanban_service.dto.request.ResetPasswordRequest;
import com.bacpham.kanban_service.dto.response.UserResponse;

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
}
