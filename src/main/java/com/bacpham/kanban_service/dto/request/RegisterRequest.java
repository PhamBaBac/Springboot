package com.bacpham.kanban_service.dto.request;
import com.bacpham.kanban_service.enums.Role;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class RegisterRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private Role role;
    private boolean mfaEnabled;
}