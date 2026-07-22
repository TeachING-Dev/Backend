package com.teaching.backend.domain.home.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum HomeSuccessCode implements BaseSuccessCode {

    HOME_DASHBOARD_SUCCESS(HttpStatus.OK, "HOME2000", "홈 대시보드 조회에 성공했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
