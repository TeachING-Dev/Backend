package com.teaching.backend.domain.payment.exception;

import com.teaching.backend.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PaymentErrorCode implements BaseErrorCode {

    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "PAYMENT4091", "이미 구독 중인 사용자입니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT4041", "결제 정보를 찾을 수 없습니다."),
    KAKAOPAY_REQUEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT5001", "카카오페이 요청 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
