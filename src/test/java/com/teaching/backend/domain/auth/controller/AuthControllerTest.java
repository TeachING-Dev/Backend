package com.teaching.backend.domain.auth.controller;

import com.teaching.backend.domain.auth.exception.AuthErrorCode;
import com.teaching.backend.domain.auth.exception.AuthException;
import com.teaching.backend.domain.auth.service.AuthService;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.util.RefreshTokenCookieUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private RefreshTokenCookieUtil refreshTokenCookieUtil;

    @InjectMocks
    private AuthController authController;

    @Test
    void reissueSucceedsWithExactlyOneRefreshTokenCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", "valid-token"));
        when(authService.reissueAccessToken("valid-token")).thenReturn("new-access-token");

        ApiResponse<String> response = authController.reissue(request);

        assertThat(response.getResult()).isEqualTo("new-access-token");
    }

    @Test
    void reissueRejectsWhenNoRefreshTokenCookiePresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> authController.reissue(request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    /**
     * domain 속성이 다른 legacy refreshToken 쿠키가 새 쿠키와 공존하면, 서버는
     * 어느 쪽이 "지금 로그인한 사용자"의 것인지 구분할 수 없다. 임의로 하나를 골라
     * 재발급하면 엉뚱한 계정으로 인증되는 사고로 이어지므로, 이 경우 재로그인을
     * 유도해야 한다 (findFirst()로 하나를 고르던 이전 동작에 대한 회귀 방지 테스트).
     */
    @Test
    void reissueRejectsWhenMultipleRefreshTokenCookiesCollide() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("refreshToken", "legacy-host-only-token-for-user-A"),
                new Cookie("refreshToken", "current-domain-scoped-token-for-user-B")
        );

        assertThatThrownBy(() -> authController.reissue(request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    void logoutRevokesTokenWhenExactlyOneCookiePresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        authController.logout(request, response);

        verify(authService).logout("valid-token");
        verify(refreshTokenCookieUtil).clear(response);
    }

    @Test
    void logoutSkipsRevokeButStillClearsCookieWhenTokensCollide() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("refreshToken", "legacy-token"),
                new Cookie("refreshToken", "current-token")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        authController.logout(request, response);

        verify(authService, never()).logout(org.mockito.ArgumentMatchers.anyString());
        verify(refreshTokenCookieUtil).clear(response);
    }
}
