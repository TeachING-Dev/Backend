package com.teaching.backend.domain.tag.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TagErrorCode implements BaseErrorCode {

    TAG_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TAG4031", "해당 태그를 삭제할 권한이 없습니다."),
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "TAG4041", "존재하지 않는 태그입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
