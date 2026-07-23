package com.teaching.backend.domain.teachingmap.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
@AllArgsConstructor
public enum TeachingMapErrorCode implements BaseErrorCode {

    FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "TEACHING_MAP_404_1", "존재하지 않는 폴더입니다."),
    FOLDER_MATERIAL_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "TEACHING_MAP_400_1", "자료 부족: 최소 3개 이상의 자료가 필요합니다."),
    AI_RESPONSE_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TEACHING_MAP_500_1", "AI 응답을 파싱하는데 실패했습니다."),
    AI_RESULT_MATERIAL_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR, "TEACHING_MAP_500_2", "AI가 반환한 자료 정보가 폴더 내 자료와 일치하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}