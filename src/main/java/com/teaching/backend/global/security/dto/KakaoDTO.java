package com.teaching.backend.global.security.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class KakaoDTO implements OAuthDTO {

    private final String providerId;
    private final String email;
    private final String nickname;

    @Override
    public String getProvider() {
        return "KAKAO";
    }
}