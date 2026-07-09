package com.teaching.backend.global.auth;

public class MockAuth {

    private MockAuth() {
    }

    // TODO: 실제 JWT 인증 연동 후 토큰에서 추출한 user_id로 교체
    public static final Long CURRENT_USER_ID = 1L;
}
