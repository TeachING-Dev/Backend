package com.teaching.backend.domain.folder.exception;

import com.teaching.backend.global.exception.GeneralException;

public class FolderException extends GeneralException {

    public FolderException(FolderErrorCode errorCode) {
        super(errorCode);
    }
}
