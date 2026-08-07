package com.teaching.backend.domain.material.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MaterialErrorCode implements BaseErrorCode {

    MATERIAL_INDEXING_TEXT_EMPTY(HttpStatus.BAD_REQUEST, "MATERIAL4009", "색인할 자료 본문이 비어 있습니다."),
    MATERIAL_INDEXING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MATERIAL5003", "자료 색인에 실패했습니다."),
    MATERIAL_EMBEDDING_FAILED(HttpStatus.BAD_GATEWAY, "MATERIAL5023", "자료 임베딩 생성에 실패했습니다."),
    MATERIAL_VECTOR_STORE_FAILED(HttpStatus.BAD_GATEWAY, "MATERIAL5024", "자료 벡터 저장에 실패했습니다."),
    INVALID_MATERIAL_ID(HttpStatus.BAD_REQUEST, "MATERIAL4001", "올바르지 않은 자료 ID입니다."),
    MATERIAL_IDS_REQUIRED(HttpStatus.BAD_REQUEST, "MATERIAL4002", "자료 ID 목록을 입력해주세요."),
    TARGET_FOLDER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "MATERIAL4003", "이동할 대상 폴더 ID를 입력해주세요."),
    SUMMARY_REQUIRED(HttpStatus.BAD_REQUEST, "MATERIAL4004", "수정할 요약 내용을 입력해주세요."),
    TITLE_REQUIRED(HttpStatus.BAD_REQUEST, "MATERIAL4005", "자료 제목을 입력해주세요."),
    ORIGINAL_URL_REQUIRED(HttpStatus.BAD_REQUEST, "MATERIAL4006", "원문 URL을 입력해주세요."),
    CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "MATERIAL4007", "분석할 본문 내용을 입력해주세요."),
    UNSUPPORTED_MATERIAL_PLATFORM(HttpStatus.BAD_REQUEST, "MATERIAL4008", "지원하지 않는 자료 플랫폼입니다."),
    MATERIAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "MATERIAL4031", "해당 자료에 접근할 권한이 없습니다."),
    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "MATERIAL4041", "자료를 찾을 수 없습니다."),
    MATERIAL_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "MATERIAL4042", "자료의 AI 분석 결과를 찾을 수 없습니다."),
    MATERIAL_NOT_IN_TRASH(HttpStatus.CONFLICT, "MATERIAL4093", "휴지통에 있는 자료가 아닙니다."),
    AI_ANALYSIS_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MATERIAL5001", "AI 자료 분석 생성에 실패했습니다."),
    AI_ANALYSIS_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MATERIAL5002", "AI 분석 응답을 해석하지 못했습니다."),
    MATERIAL_CONTENT_EXTRACTION_FAILED(HttpStatus.BAD_GATEWAY, "MATERIAL5021", "자료 원문 추출에 실패했습니다."),
    MATERIAL_CONTENT_EMPTY(HttpStatus.BAD_GATEWAY, "MATERIAL5022", "자료 원문이 비어 있습니다."),
    DETAIL_ANALYSIS_REQUIRED(HttpStatus.BAD_REQUEST, "MATERIAL4010", "수정할 상세 분석 내용을 입력해주세요."),
    DETAIL_ANALYSIS_IMAGE_MODIFIED(HttpStatus.BAD_REQUEST, "MATERIAL4011", "상세 분석에 포함된 이미지는 수정할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
