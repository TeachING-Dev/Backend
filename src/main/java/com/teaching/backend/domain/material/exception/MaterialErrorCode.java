package com.teaching.backend.domain.material.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MaterialErrorCode implements BaseErrorCode {

    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "MATERIAL4041", "자료를 찾을 수 없습니다."),
    MATERIAL_NOT_IN_TRASH(HttpStatus.CONFLICT, "MATERIAL4093", "휴지통에 있는 자료가 아닙니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
