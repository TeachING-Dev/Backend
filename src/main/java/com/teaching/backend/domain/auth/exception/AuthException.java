package com.teaching.backend.domain.auth.exception;

import com.teaching.backend.global.exception.GeneralException;

public class AuthException extends GeneralException {

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }
}

