package com.teaching.backend.domain.chat.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {

    CHATROOM_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "CHAT400_1", "무료 회원은 대화방을 최대 10개까지 생성할 수 있습니다."),
    DAILY_QUESTION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "CHAT400_2", "무료 회원은 하루 최대 5회까지 질문할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
