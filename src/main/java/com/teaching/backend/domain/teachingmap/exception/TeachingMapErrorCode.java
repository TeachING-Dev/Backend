package com.teaching.backend.domain.teachingmap.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TeachingMapErrorCode implements BaseErrorCode {

    TEACHING_MAP_NOT_FOUND(HttpStatus.NOT_FOUND, "TMAP4041", "티칭맵을 찾을 수 없습니다."),
    TEACHING_MAP_NOT_IN_TRASH(HttpStatus.CONFLICT, "TMAP4093", "휴지통에 있는 티칭맵이 아닙니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
