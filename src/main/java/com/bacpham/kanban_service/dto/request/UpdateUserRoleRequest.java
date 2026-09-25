package com.bacpham.kanban_service.dto.request;

import com.bacpham.kanban_service.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserRoleRequest {

    @NotNull(message = "Vai trò (Role) không được để trống")
    private Role role;
}
