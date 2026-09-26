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

@Entity
@Table(name = "user", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email")
})
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseModel implements UserDetails {

    @Column(nullable = false, length = 50)
    String firstname;

    @Column(nullable = false, length = 50)
    String lastname;

    @Column(nullable = false, unique = true, length = 100)
    String email;

    @Column(nullable = false)
    @JsonIgnore
    String password;

    @Column(columnDefinition = "TEXT")
    String avatarUrl;

    @Column(nullable = false)
    boolean mfaEnabled = false;

    @Column(length = 255)
    @JsonIgnore
    @Convert(converter = com.bacpham.kanban_service.utils.crypto.TwoFactorSecretEncryptor.class)
    String secret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    Provider provider;

    @Column(length = 100)
    String providerId;

    @ToString.Exclude
    @OneToMany(mappedBy = "createdBy", cascade = CascadeType.ALL)
    List<Cart> cartItems;

    @ToString.Exclude
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    List<Order> orders;

    @ToString.Exclude
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    List<ChatHistory> chatHistories;

    @ToString.Exclude
    @OneToMany(mappedBy = "createdBy", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Address> addresses;


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role.getAuthorities();
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
