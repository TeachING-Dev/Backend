package com.teaching.backend.global.security;

import org.springframework.stereotype.Component;

/**
 * 개발용 현재 사용자 제공자.
 * 항상 고정된 사용자(id = 1)를 "현재 로그인 사용자"로 간주한다.
 *
 * DevDataInitializer 가 local 프로파일에서 id=1 데모 사용자를 만들어 두므로,
 * 로그인 없이도 /users/me 가 동작한다.
 *
 * TODO: 실제 인증 연동 시 이 클래스를 제거하고 SecurityContext 기반 구현으로 교체.
 */
@Component
public class DevCurrentUserProvider implements CurrentUserProvider {

    private static final Long DEV_USER_ID = 1L;

    @Override
    public Long getCurrentUserId() {
        return DEV_USER_ID;
    }
}
