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
    AI_RESULT_MATERIAL_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR, "TEACHING_MAP_500_2", "AI가 반환한 자료 정보가 폴더 내 자료와 일치하지 않습니다."),
    AI_RESULT_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "TEACHING_MAP_500_3", "AI 응답이 유효하지 않습니다."),
    HIGHLIGHT_AI_RESPONSE_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TEACHING_MAP_500_4", "하이라이트 AI 응답을 파싱하는데 실패했습니다."),
    TEACHING_MAP_IDS_REQUIRED(HttpStatus.BAD_REQUEST, "TMAP4002", "티칭맵 ID 목록을 입력해주세요."),


    TEACHING_MAP_NOT_FOUND(HttpStatus.NOT_FOUND, "TEACHING_MAP_404_2", "존재하지 않는 티칭맵입니다."),
    STEP_NOT_FOUND(HttpStatus.NOT_FOUND, "TEACHING_MAP_404_3", "존재하지 않는 스텝입니다."),
    MATERIAL_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "TEACHING_MAP_404_4", "자료 분석 결과를 찾을 수 없습니다."),
    HIGHLIGHT_NOT_FOUND(HttpStatus.NOT_FOUND, "TEACHING_MAP_404_5", "존재하지 않는 하이라이트입니다."),
    HIGHLIGHT_MATERIAL_MISMATCH(HttpStatus.BAD_REQUEST, "TEACHING_MAP_400_2", "해당 하이라이트는 요청한 자료에 속하지 않습니다."),
    TEACHING_MAP_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "TMAP4003", "다른 요청에 의해 티칭맵이 변경되었습니다. 다시 시도해주세요."),
    INVALID_TEACHING_MAP_TYPE(HttpStatus.BAD_REQUEST , "TMAP4004","모드는 shortcut,deepdive 두 모드 중 하나여야 합니다."),
    TEACHING_MAP_ALREADY_FINISHED(HttpStatus.BAD_REQUEST,"TMAP4005","이미 생성이 완료된 티칭맵은 임시저장할 수 없습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
