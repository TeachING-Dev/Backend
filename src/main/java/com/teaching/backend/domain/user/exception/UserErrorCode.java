package com.teaching.backend.domain.user.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "존재하지 않는 사용자입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER409", "이미 가입된 이메일입니다."),

    SOCIAL_INFO_NOT_FOUND(HttpStatus.BAD_REQUEST, "USER400_2", "소셜 계정 정보를 가져올 수 없습니다."),
    EMAIL_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "USER400_3", "이메일 제공에 동의해주세요."),

    // 마이페이지
    NICKNAME_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "USER400_4", "닉네임은 2~10자의 한글, 영문, 숫자만 가능합니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "USER409_2", "이미 사용 중인 닉네임입니다."),
    PROFILE_NO_UPDATE_FIELD(HttpStatus.BAD_REQUEST, "USER400_5", "수정할 값을 최소 1개 이상 입력해주세요."),
    PROFILE_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "USER400_6", "프로필 이미지 URL 형식이 올바르지 않습니다."),
    NOTIFICATION_INVALID(HttpStatus.BAD_REQUEST, "USER400_7", "알림 수신 여부 값이 올바르지 않습니다."),

    // 회원 탈퇴
    WITHDRAWAL_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "USER400_8", "탈퇴 사유를 선택해주세요."),
    WITHDRAWAL_REASON_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "USER400_9", "기타 사유를 입력해주세요."),
    WITHDRAWAL_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "USER400_10", "탈퇴 확인에 동의해주세요."),
    WITHDRAWAL_REASON_DETAIL_TOO_LONG(HttpStatus.BAD_REQUEST, "USER400_11", "탈퇴 사유 상세는 500자를 초과할 수 없습니다."),

    // 티칭맵 설정
    TEACHER_PERSONA_INVALID(HttpStatus.BAD_REQUEST, "USER400_12", "올바른 AI 선생님을 선택해주세요.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
