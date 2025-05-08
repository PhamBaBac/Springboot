package com.bacpham.kanban_service.dto.request;

import com.bacpham.kanban_service.validator.DobConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {
    @NotNull(message = "Username cannot be null")
    @Size(min = 5, message = "INVALID_USERNAME")
    String username;

    @NotNull(message = "Email cannot be null")
    String email;
    @Size(min = 8, message = "INVALID_PASSWORD")
    String password;
    String firstName;
    String lastName;

    @DobConstraint(min=18, message = "INVALID_DOB")
    LocalDate dob;
}
