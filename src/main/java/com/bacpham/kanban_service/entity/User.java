package com.bacpham.kanban_service.entity;

import com.bacpham.kanban_service.enums.Provider;
import com.bacpham.kanban_service.enums.Role;
import com.bacpham.kanban_service.helper.base.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.Collection;
import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseModel implements UserDetails {

    String firstname;
    String lastname;
    String email;
    String password;
    boolean mfaEnabled;
    String secret;

    @Enumerated(EnumType.STRING)
    Role role;

    @Enumerated(EnumType.STRING)
    Provider provider;

    String providerId;
    @OneToMany(mappedBy = "createdBy", cascade = CascadeType.ALL)
    List<Cart> cartItems;


    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    List<Order> orders;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    List<ChatHistory> chatHistories;

    @OneToMany(mappedBy = "createdBy", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Address> addresses;
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role.getAuthorities();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}