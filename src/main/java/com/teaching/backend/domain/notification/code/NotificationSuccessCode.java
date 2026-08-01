package com.teaching.backend.domain.notification.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationSuccessCode implements BaseSuccessCode {

    NOTIFICATION_LIST_SUCCESS(HttpStatus.OK, "NOTIFICATION2000", "알림 목록 조회에 성공했습니다."),
    NOTIFICATION_READ_SUCCESS(HttpStatus.OK, "NOTIFICATION2001", "알림 읽음 처리가 완료되었습니다."),
    NOTIFICATION_SUMMARY_SUCCESS(HttpStatus.OK, "NOTIFICATION2002", "알림 요약 조회에 성공했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
