package com.teaching.backend.domain.material.exception;

import com.teaching.backend.global.exception.GeneralException;

public class MaterialException extends GeneralException {

    public MaterialException(MaterialErrorCode errorCode) {
        super(errorCode);
    }
}
