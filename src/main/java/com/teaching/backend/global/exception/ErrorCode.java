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

    // 사용자 & 마이페이지
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER4041", "존재하지 않거나 탈퇴한 사용자입니다."),
    NICKNAME_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "USER4000", "닉네임은 2~10자의 한글, 영문, 숫자만 가능합니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "USER4090", "이미 사용 중인 닉네임입니다."),
    PROFILE_NO_UPDATE_FIELD(HttpStatus.BAD_REQUEST, "COMMON4000", "수정할 값을 최소 1개 이상 입력해주세요."),
    PROFILE_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "USER4001", "프로필 이미지 URL 형식이 올바르지 않습니다."),
    NOTIFICATION_INVALID(HttpStatus.BAD_REQUEST, "COMMON4000", "알림 수신 여부 값이 올바르지 않습니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
