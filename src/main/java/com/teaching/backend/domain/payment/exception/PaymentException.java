package com.teaching.backend.domain.payment.exception;

import com.teaching.backend.global.exception.GeneralException;

public class PaymentException extends GeneralException {

    public PaymentException(PaymentErrorCode errorCode) {
        super(errorCode);
    }
}
