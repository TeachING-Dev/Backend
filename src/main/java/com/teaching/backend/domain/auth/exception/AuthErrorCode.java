package com.teaching.backend.domain.auth.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "USER401_1", "리프레시 토큰이 존재하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "USER401_2", "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "USER401_3", "만료된 리프레시 토큰입니다."),
    SOCIAL_INFO_NOT_FOUND(HttpStatus.BAD_REQUEST, "USER400_1", "소셜 계정 정보를 가져올 수 없습니다."),
    NOT_SUPPORT_SOCIAL_PROVIDER(HttpStatus.BAD_REQUEST, "USER400_1", "지원하지 않는 소셜 로그인입니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}

