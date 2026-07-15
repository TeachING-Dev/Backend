package com.teaching.backend.domain.user.exception;

import com.teaching.backend.global.exception.GeneralException;

public class UserException extends GeneralException {

    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }
}