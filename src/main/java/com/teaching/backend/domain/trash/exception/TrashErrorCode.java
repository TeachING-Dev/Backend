package com.teaching.backend.domain.trash.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TrashErrorCode implements BaseErrorCode {

    INVALID_SORT(HttpStatus.BAD_REQUEST, "TRASH4001", "지원하지 않는 정렬 방식입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
