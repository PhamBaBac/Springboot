package com.bacpham.kanban_service.entity;

import java.time.LocalDate;
import java.util.Set;

import com.bacpham.kanban_service.helper.base.model.BaseModel;
import jakarta.persistence.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class User extends BaseModel {
    String username;
    String email;
    String password;
    String firstName;
    String lastName;
    LocalDate dob;

    @ManyToMany
    Set<Role> roles;
}