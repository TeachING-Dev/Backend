package com.teaching.backend.domain.payment.code;

import com.teaching.backend.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PaymentSuccessCode implements BaseSuccessCode {

    PAYMENT_READY_SUCCESS(HttpStatus.OK, "PAYMENT2000", "결제 준비가 완료되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
