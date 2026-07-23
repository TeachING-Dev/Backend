package com.teaching.backend.domain.teachingmap.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TeachingMapSuccessCode implements BaseSuccessCode {

    TEACHING_MAP_LIST_SUCCESS(HttpStatus.OK, "TMAP2000", "티칭맵 목록 조회에 성공했습니다."),
    TEACHING_MAP_CREATE_SUCCESS(HttpStatus.CREATED,"TMAP2001","티칭맵 생성에 성공하였씁니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
