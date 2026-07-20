package com.teaching.backend.global.security;

import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.global.security.entity.AuthMember;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * JwtAuthFilter 가 SecurityContext 에 심어둔 인증 정보(AuthMember)에서 실제 로그인한 사용자 id 를 꺼낸다.
 * SecurityConfig 가 이 API들을 인증 필요 구간으로 막아두므로, 이 시점엔 인증된 사용자만 도달한다.
 */
@Component
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthMember authMember)) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }
        return authMember.getUserId();
    }
}
