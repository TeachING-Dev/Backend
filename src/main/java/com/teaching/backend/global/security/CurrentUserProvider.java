package com.teaching.backend.global.security;

/**
 * 현재 로그인한 사용자를 제공하는 추상화.
 *
 * SecurityContext(JWT 인증 결과)에서 실제 사용자 id 를 꺼내는 구현체(SecurityCurrentUserProvider)를 사용한다.
 * 이 인터페이스를 쓰는 서비스/컨트롤러는 구현체 교체와 무관하게 그대로 유지된다.
 */
public interface CurrentUserProvider {

    Long getCurrentUserId();
}
