package com.teaching.backend.global.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SuccessCode {

    COMMON200("COMMON200", "요청에 성공했습니다."),

    // 사용자 & 마이페이지
    USER_INFO_FOUND("USER2000", "내 정보를 조회하였습니다."),
    PROFILE_UPDATED("USER2001", "프로필을 수정하였습니다."),
    NOTIFICATION_UPDATED("USER2004", "알림 설정을 변경하였습니다.");

    private final String code;
    private final String message;
}
