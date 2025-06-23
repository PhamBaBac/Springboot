package com.bacpham.kanban_service.dto.request;


import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ResetPasswordRequest {
    private String email;
    private String newPassword;
}