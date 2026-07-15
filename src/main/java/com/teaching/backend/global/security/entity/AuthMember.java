package com.teaching.backend.global.security.entity;

import com.teaching.backend.user.entity.User;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter

public class AuthMember implements UserDetails {

    private final User user;

    private AuthMember(User user) {
        this.user = user;
    }

    // AuthMember.java
    public static AuthMember from(User user) {
        return new AuthMember(user);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override
    public @Nullable String getPassword() {
        return null;
    }

    public Long getUserId() {
        return user.getId();
    }
    @Override
    public String getUsername() {
        return user.getEmail();
    }

}