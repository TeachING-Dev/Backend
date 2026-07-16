package com.teaching.backend.domain.folder.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FolderSuccessCode implements BaseSuccessCode {

    FOLDER_LIST_SUCCESS(HttpStatus.OK, "FOLDER2000", "폴더 목록 조회에 성공했습니다."),
    FOLDER_DETAIL_SUCCESS(HttpStatus.OK, "FOLDER2001", "폴더 상세 조회에 성공했습니다."),
    FOLDER_RENAME_SUCCESS(HttpStatus.OK, "FOLDER2002", "폴더명이 수정되었습니다."),
    FOLDER_TRASH_SUCCESS(HttpStatus.OK, "FOLDER2003", "폴더가 휴지통으로 이동되었습니다."),
    FOLDER_RESTORE_SUCCESS(HttpStatus.OK, "FOLDER2004", "폴더가 복구되었습니다."),
    FOLDER_CREATE_SUCCESS(HttpStatus.CREATED, "FOLDER2010", "폴더가 생성되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
