package com.teaching.backend.domain.trash.exception;

import com.teaching.backend.global.exception.GeneralException;

public class TrashException extends GeneralException {

    public TrashException(TrashErrorCode errorCode) {
        super(errorCode);
    }
}
