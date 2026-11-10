package com.bacpham.kanban_service.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @JsonAlias({"firstname"})
    private String firstName;

    @JsonAlias({"lastname"})
    private String lastName;

    private String phoneNumber;

    @JsonAlias({"photoURL", "avatar"})
    private String avatarUrl;
}
