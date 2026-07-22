package com.teaching.backend.domain.material.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MaterialSuccessCode implements BaseSuccessCode {

    MATERIAL_LIST_SUCCESS(HttpStatus.OK, "MATERIAL2000", "수집 지식 목록 조회에 성공했습니다."),
    FOLDER_MATERIAL_LIST_SUCCESS(HttpStatus.OK, "MATERIAL2100", "폴더 내부 자료 목록 조회에 성공했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
