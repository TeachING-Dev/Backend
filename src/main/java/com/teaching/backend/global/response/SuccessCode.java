package com.teaching.backend.global.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessCode {

    CHATROOM_LIST_OK(HttpStatus.OK, "CHAT2000", "대화방 목록을 조회하였습니다."),
    CHATROOM_HISTORY_OK(HttpStatus.OK, "CHAT2001", "대화 내역을 조회하였습니다."),
    ANSWER_OK(HttpStatus.OK, "CHAT2002", "답변을 생성하였습니다."),
    ANSWER_FALLBACK(HttpStatus.OK, "CHAT2003", "보관함에서 관련 자료를 찾지 못해 일반 지식으로 답변하였습니다."),
    CHATROOM_DELETED(HttpStatus.OK, "CHAT2004", "대화방을 삭제하였습니다."),
    CHATROOM_CREATED(HttpStatus.CREATED, "CHAT2010", "새 대화방을 생성하였습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
