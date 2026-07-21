package com.teaching.backend.domain.user.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserSuccessCode implements BaseSuccessCode {

    // 마이페이지
    USER_INFO_FOUND(HttpStatus.OK, "USER2000", "내 정보를 조회하였습니다."),
    PROFILE_UPDATED(HttpStatus.OK, "USER2001", "프로필을 수정하였습니다."),
    TEACHER_PERSONA_UPDATED(HttpStatus.OK, "USER2003", "AI 선생님 설정을 변경하였습니다."),
    NOTIFICATION_UPDATED(HttpStatus.OK, "USER2004", "알림 설정을 변경하였습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
