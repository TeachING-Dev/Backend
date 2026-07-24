package com.teaching.backend.domain.auth.controller;

import com.teaching.backend.domain.auth.code.AuthSuccessCode;
import com.teaching.backend.domain.auth.dto.SignupRequest;
import com.teaching.backend.domain.auth.exception.AuthErrorCode;
import com.teaching.backend.domain.auth.exception.AuthException;
import com.teaching.backend.domain.auth.service.AuthService;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import com.teaching.backend.global.security.util.RefreshTokenCookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

@Tag(name = "Auth", description = "인증/인가 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor

public class AuthController {

    private final AuthService authService;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    //refresh Token 발급 api

    @Operation(
            summary = "AccessToken 재발급",
            description = "쿠키에 담긴 refreshToken을 검증하여 새로운 accessToken을 발급합니다."
    )
    @PostMapping("/reissue")
    public ApiResponse<String> reissue(HttpServletRequest request) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        String newAccessToken = authService.reissueAccessToken(refreshToken);
        return ApiResponse.onSuccess(newAccessToken);
    }


    @Operation(
            summary = "회원가입",
            description = "닉네임을 확정하고 약관 동의 정보를 확인해 회원을 최종 등록합니다."
    )
    @PostMapping("/signup")
    public ApiResponse<Void> signup(@AuthenticationPrincipal AuthMember authMember,//로그인 유저 정보
                                    @Valid @RequestBody SignupRequest request) {
        Long userId = authMember.getUserId();
        authService.signup(userId,request);
        return ApiResponse.onSuccess(AuthSuccessCode.SIGNUP_COMPLETED,null);
    }

    @Operation(
            summary = "로그아웃",
            description = "쿠키에 담긴 refreshToken을 무효화하고, 쿠키를 만료시킵니다."
    )
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        extractRefreshTokenFromCookieOrEmpty(request).ifPresent(authService::logout);
        RefreshTokenCookieUtil.clear(response, cookieSecure);
        return ApiResponse.onSuccess(AuthSuccessCode.LOGOUT_SUCCESS, null);
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {

            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }



        return java.util.Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals("refreshToken"))
                .findFirst()
                .map(jakarta.servlet.http.Cookie::getValue)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }

    /** 로그아웃은 토큰이 없어도 실패시키지 않아야 해서, 예외 대신 Optional로 처리하는 별도 버전. */
    private Optional<String> extractRefreshTokenFromCookieOrEmpty(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals("refreshToken"))
                .findFirst()
                .map(Cookie::getValue);
    }

    @Operation(
            summary = "닉네임 중복 확인",
            description = "닉네임 형식(10자 이내)과 중복 여부를 검증합니다. 통과 시 200 응답, 형식 오류나 중복 시 예외가 발생합니다."
    )
    @GetMapping("/check-nickname")
    public ApiResponse<Void> checkNickname(@RequestParam String nickname) {
        authService.validateNickname(nickname);
        return ApiResponse.onSuccess(AuthSuccessCode.NICKNAME_AVAILABLE,null);
    }
    }
