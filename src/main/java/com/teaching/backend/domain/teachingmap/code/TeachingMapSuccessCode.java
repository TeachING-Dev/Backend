package com.teaching.backend.domain.teachingmap.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TeachingMapSuccessCode implements BaseSuccessCode {

    TEACHING_MAP_RESTORE_SUCCESS(HttpStatus.OK, "TMAP2004", "해당 티칭맵이 성공적으로 복구되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
