package com.bacpham.kanban_service.dto.request;

import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.validator.StrongPassword;
import com.bacpham.kanban_service.validator.ValidEmail;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "INVALID_SIZE_FIRST_NAME")
    @Pattern(regexp = "^[\\p{L}\\p{M} ]+$", message = "INVALID_FIRST_NAME_PATTERN")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "INVALID_SIZE_LAST_NAME")
    private String lastName;

    @NotBlank(message = "Email is required")
    @ValidEmail(message = "INVALID_EMAIL")
    private String email;

    @NotBlank(message = "Password is required")
    @StrongPassword(message = "INVALID_PASSWORD")
    private String password;


    @Builder.Default
    private Role role = Role.USER;

    private boolean mfaEnabled;

    private String captchaToken;
}
