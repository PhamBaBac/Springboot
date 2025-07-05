package com.bacpham.kanban_service.dto.response;

import com.bacpham.kanban_service.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private String id;
    private String firstname;
    private String lastname;
    private String email;
    private String avatarUrl;
    private Role role;
    private boolean mfaEnabled;
}
