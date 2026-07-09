package com.teaching.backend.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류가 발생했습니다."),

    CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT4040", "존재하지 않는 대화방입니다."),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4010", "인증 정보가 유효하지 않습니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH4030", "접근 권한이 없습니다."),
    CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "CHAT4000", "질문 내용을 입력해주세요."),
    // TODO: 무료/프리미엄 판정 로직 확정 후 대화방 생성 API에 적용
    CHATROOM_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "CHAT4030", "무료 회원은 최대 10개의 대화방만 생성할 수 있습니다."),
    // TODO: 무료 회원 당일 질문 횟수 집계/초기화(KST 00:00) 정책 확정 후 적용
    DAILY_QUESTION_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "CHAT4290", "일일 제한 횟수(5회)를 초과하였습니다. 무제한 이용은 프리미엄 플랜을 구독하세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}