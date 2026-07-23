package com.teaching.backend.domain.tag.exception;

import com.teaching.backend.global.exception.GeneralException;

public class TagException extends GeneralException {

    public TagException(TagErrorCode errorCode) {
        super(errorCode);
    }
}
