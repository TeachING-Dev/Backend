package com.teaching.backend.domain.material.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MaterialErrorCode implements BaseErrorCode {

    INVALID_MATERIAL_ID(HttpStatus.BAD_REQUEST, "MATERIAL4001", "올바르지 않은 자료 ID입니다."),
    MATERIAL_IDS_REQUIRED(HttpStatus.BAD_REQUEST, "MATERIAL4002", "자료 ID 목록을 입력해주세요."),
    TARGET_FOLDER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "MATERIAL4003", "이동할 대상 폴더 ID를 입력해주세요."),
    SUMMARY_REQUIRED(HttpStatus.BAD_REQUEST, "MATERIAL4004", "수정할 요약 내용을 입력해주세요."),
    MATERIAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "MATERIAL4031", "해당 자료에 접근할 권한이 없습니다."),
    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "MATERIAL4041", "자료를 찾을 수 없습니다."),
    MATERIAL_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "MATERIAL4042", "자료의 AI 분석 결과를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
