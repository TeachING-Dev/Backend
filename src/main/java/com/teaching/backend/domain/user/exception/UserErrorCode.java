package com.teaching.backend.domain.user.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "존재하지 않는 사용자입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER409", "이미 가입된 이메일입니다."),

    SOCIAL_INFO_NOT_FOUND(HttpStatus.BAD_REQUEST, "USER400_2", "소셜 계정 정보를 가져올 수 없습니다."),
    EMAIL_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "USER400_3", "이메일 제공에 동의해주세요.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
