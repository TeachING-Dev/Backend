package com.teaching.backend.domain.material.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MaterialSuccessCode implements BaseSuccessCode {

    FOLDER_MATERIAL_LIST_SUCCESS(HttpStatus.OK, "MATERIAL2100", "폴더 내부 자료 목록 조회에 성공했습니다."),
    MATERIAL_DETAIL_SUCCESS(HttpStatus.OK, "MATERIAL2101", "자료 상세 조회에 성공했습니다."),
    MATERIAL_ORIGIN_URL_SUCCESS(HttpStatus.OK, "MATERIAL2102", "원문 URL 조회에 성공했습니다."),
    MATERIAL_MOVE_SUCCESS(HttpStatus.OK, "MATERIAL2103", "자료가 이동되었습니다."),
    MATERIAL_TRASH_SUCCESS(HttpStatus.OK, "MATERIAL2104", "자료가 휴지통으로 이동되었습니다."),
    MATERIAL_RESTORE_SUCCESS(HttpStatus.OK, "MATERIAL2105", "자료가 복구되었습니다."),
    MATERIAL_ANALYSIS_SUCCESS(HttpStatus.OK, "ANALYSIS2100", "AI 분석 조회에 성공했습니다."),
    MATERIAL_ANALYSIS_SUMMARY_UPDATE_SUCCESS(HttpStatus.OK, "ANALYSIS2101", "AI 요약이 수정되었습니다."),
    MATERIAL_TAG_LIST_SUCCESS(HttpStatus.OK, "TAG2100", "태그 조회에 성공했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
