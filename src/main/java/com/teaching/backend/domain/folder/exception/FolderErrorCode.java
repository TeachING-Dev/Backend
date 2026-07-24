package com.teaching.backend.domain.folder.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FolderErrorCode implements BaseErrorCode {

    INVALID_SORT(HttpStatus.BAD_REQUEST, "FOLDER4001", "지원하지 않는 정렬 방식입니다."),
    FOLDER_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "FOLDER4002", "폴더명을 입력해주세요."),
    FOLDER_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "FOLDER4003", "폴더명은 최대 10자까지 입력 가능합니다."),
    FOLDER_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "FOLDER4004", "폴더는 최대 6개까지 생성할 수 있습니다."),
    INVALID_FOLDER_ID(HttpStatus.BAD_REQUEST, "FOLDER4005", "올바르지 않은 폴더 ID입니다."),
    FOLDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FOLDER4031", "해당 폴더에 접근할 권한이 없습니다."),
    FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "FOLDER4041", "폴더를 찾을 수 없습니다."),
    DUPLICATE_FOLDER_NAME(HttpStatus.CONFLICT, "FOLDER4091", "이미 존재하는 폴더명입니다."),
    FOLDER_ALREADY_DELETED(HttpStatus.CONFLICT, "FOLDER4092", "이미 휴지통으로 이동된 폴더입니다."),
    PARENT_FOLDER_IN_TRASH(HttpStatus.CONFLICT, "FOLDER4093", "소속된 상위 폴더가 휴지통에 있습니다. 폴더를 먼저 복구해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
