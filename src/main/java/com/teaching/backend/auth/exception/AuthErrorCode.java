package com.teaching.backend.auth.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "USER401_1", "리프레시 토큰이 존재하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "USER401_2", "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "USER401_3", "만료된 리프레시 토큰입니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}

