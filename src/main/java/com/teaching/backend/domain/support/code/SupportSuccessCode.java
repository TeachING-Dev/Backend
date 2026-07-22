package com.teaching.backend.domain.support.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SupportSuccessCode implements BaseSuccessCode {

    CONTACTS_FOUND(HttpStatus.OK, "SUPPORT2000", "문의 채널을 조회하였습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
