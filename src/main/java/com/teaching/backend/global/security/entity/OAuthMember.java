package com.teaching.backend.global.security.entity;

import com.teaching.backend.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Getter
public class OAuthMember implements OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;

    private OAuthMember(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    public static OAuthMember from(User user, Map<String, Object> attributes) {
        return new OAuthMember(user, attributes);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getName() {
        return String.valueOf(user.getId());
    }
}