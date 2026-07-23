package com.teaching.backend.domain.tag.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TagSuccessCode implements BaseSuccessCode {

    TAG_DELETE_SUCCESS(HttpStatus.OK, "TAG2002", "태그가 성공적으로 삭제되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
