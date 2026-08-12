package com.teaching.backend.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@Tag(name = "Auth", description = "인증/인가 관련 API")
@RestController
public class OAuth2RedirectTempController {

    @Operation(
            summary = "[dev 전용] OAuth2 로그인 성공 리다이렉트 수신",
            description = "로컬 개발 환경에서 프론트엔드 없이 OAuth2 로그인 플로우를 테스트하기 위한 임시 엔드포인트. "
                    + "OAuthSuccessHandler가 app.oauth2.redirect-uri 설정값으로 리다이렉트하는 대상이며, "
                    + "prod 프로필에서는 비활성화됨(@Profile(\"dev\"))."
    )
    @GetMapping("/oauth2/redirect")
    public String receiveOAuthRedirect(
            @Parameter(description = "OAuthSuccessHandler가 발급한 JWT accessToken")
            @RequestParam String accessToken,
            @Parameter(description = "신규 가입 여부")
            @RequestParam(required = false) Boolean isNewUser
    ) {
        return "로그인 성공!" ;
    }
}