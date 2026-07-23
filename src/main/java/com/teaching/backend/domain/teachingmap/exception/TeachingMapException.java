package com.teaching.backend.domain.teachingmap.exception;

import com.teaching.backend.domain.term.exception.TermErrorCode;
import com.teaching.backend.global.exception.GeneralException;

public class TeachingMapException extends GeneralException {

    public TeachingMapException(TeachingMapErrorCode errorCode) {
        super(errorCode);
    }

}
