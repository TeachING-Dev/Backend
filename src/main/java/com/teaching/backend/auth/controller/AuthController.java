package com.teaching.backend.auth.controller;

import com.teaching.backend.auth.exception.AuthErrorCode;
import com.teaching.backend.auth.exception.AuthException;
import com.teaching.backend.auth.service.AuthService;
import com.teaching.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증/인가 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor

public class AuthController {

    private final AuthService authService;

    //refresh Token 발급

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

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            System.out.println("쿠키 자체가 없음!");
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }


        // 임시 디버깅 코드
        for (Cookie c : request.getCookies()) {
            System.out.println("받은 쿠키 이름: " + c.getName() + " / 값: " + c.getValue());
        }

        return java.util.Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals("refreshToken"))
                .findFirst()
                .map(jakarta.servlet.http.Cookie::getValue)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }
    


    }
