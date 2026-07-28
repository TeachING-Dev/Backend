package com.teaching.backend.domain.teachingmap.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TeachingMapSuccessCode implements BaseSuccessCode {

    TEACHING_MAP_LIST_SUCCESS(HttpStatus.OK, "TMAP2000", "티칭맵 목록 조회에 성공했습니다."),
    TEACHING_MAP_CREATE_SUCCESS(HttpStatus.CREATED, "TMAP2001", "티칭맵 생성에 성공하였습니다."),
    TEACHING_MAP_RESTORE_SUCCESS(HttpStatus.OK, "TMAP2004", "해당 티칭맵이 성공적으로 복구되었습니다."),
    TEACHING_MAP_DETAIL_SUCCESS(HttpStatus.OK, "TMAP2005", "티칭맵 단건 조회에 성공했습니다."),
    TEACHING_MAP_STEP_DETAIL_SUCCESS(HttpStatus.OK, "TMAP2003", "스텝 상세 내용 조회에 성공하였습니다."),
    HIGHLIGHT_ANALYSIS_SUCCESS(HttpStatus.OK, "TMAP2002", "하이라이트 분석 조회에 성공하였습니다."),
    STEP_TOGGLE_SUCCESS(HttpStatus.OK, "TMAP2006", "스텝 완료 상태가 변경되었습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
