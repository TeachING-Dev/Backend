package com.teaching.backend.global.security.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Getter
@RequiredArgsConstructor
public class GoogleDTO implements OAuthDTO{

    private final String providerId;
    private final String email;
    private final String nickname;

    @Override
    public String getProvider()
    {
        return "GOOGLE";

    }
}
