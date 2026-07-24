package com.teaching.backend.domain.trash.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TrashSuccessCode implements BaseSuccessCode {

    TRASH_FOLDER_LIST_SUCCESS(HttpStatus.OK, "TRASH2001", "휴지통 내 폴더 목록을 성공적으로 조회했습니다."),
    TRASH_MATERIAL_LIST_SUCCESS(HttpStatus.OK, "TRASH2002", "휴지통 내 지식 자료 목록을 조회했습니다."),
    TRASH_TEACHING_MAP_LIST_SUCCESS(HttpStatus.OK, "TRASH2003", "휴지통 내 티칭맵 목록을 성공적으로 조회했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
