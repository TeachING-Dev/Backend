package com.teaching.backend.domain.term.exception;

import com.teaching.backend.global.exception.GeneralException;

public class TermException extends GeneralException {

    public TermException(TermErrorCode errorCode) {
        super(errorCode);
    }
}