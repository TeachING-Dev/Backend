package com.teaching.backend.domain.auth.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    LOGOUT_SUCCESS(HttpStatus.OK, "AUTH2001", "로그아웃되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
