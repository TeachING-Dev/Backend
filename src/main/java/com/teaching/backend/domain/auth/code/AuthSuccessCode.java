package com.teaching.backend.domain.auth.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    LOGOUT_SUCCESS(HttpStatus.OK, "AUTH2001", "로그아웃되었습니다."),
    SIGNUP_COMPLETED(HttpStatus.OK, "AUTH2000", "회원가입이 완료되었습니다."),
    NICKNAME_AVAILABLE(HttpStatus.OK,"AUTH2002","사용가능한 닉네임입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
