package com.brewhaven.security;

import com.brewhaven.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String name;
    private final String username;
    private final String email;
    private final String phone;
    private final String password;
    private final boolean mustChangePassword;
    private final java.time.LocalDateTime passwordChangedAt;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String name, String username, String email, String phone, String password, boolean mustChangePassword, java.time.LocalDateTime passwordChangedAt, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.name = name;
        this.username = username != null && !username.trim().isEmpty()
                ? username.trim()
                : (email != null && !email.trim().isEmpty() ? email.trim() : (phone != null ? phone.trim() : String.valueOf(id)));
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.mustChangePassword = mustChangePassword;
        this.passwordChangedAt = passwordChangedAt;
        this.authorities = authorities;
    }

    public UserPrincipal(Long id, String name, String username, String email, String phone, String password, Collection<? extends GrantedAuthority> authorities) {
        this(id, name, username, email, phone, password, false, null, authorities);
    }

    public static UserPrincipal create(User user) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().name());
        String effectiveUsername = user.getUsername();
        if (effectiveUsername == null || effectiveUsername.trim().isEmpty()) {
            effectiveUsername = user.getEmail() != null && !user.getEmail().trim().isEmpty()
                    ? user.getEmail().trim()
                    : (user.getPhone() != null && !user.getPhone().trim().isEmpty() ? user.getPhone().trim() : user.getName());
        }
        return new UserPrincipal(
                user.getId(),
                user.getName(),
                effectiveUsername,
                user.getEmail(),
                user.getPhone(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getPasswordChangedAt(),
                Collections.singletonList(authority)
        );
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public java.time.LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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
