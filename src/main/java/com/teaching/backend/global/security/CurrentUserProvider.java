package com.teaching.backend.global.security;

/**
 * 현재 로그인한 사용자를 제공하는 추상화.
 *
 * 아직 인증(로그인) 기능이 없으므로 개발용 구현(DevCurrentUserProvider)이
 * 고정 사용자 id 를 반환한다.
 * 인증이 붙으면 SecurityContext(JWT 등)에서 실제 사용자 id 를 꺼내는
 * 구현으로 교체하면 되고, 이 인터페이스를 쓰는 서비스/컨트롤러는 바뀌지 않는다.
 */
public interface CurrentUserProvider {

    Long getCurrentUserId();
}
