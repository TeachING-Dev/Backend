package com.teaching.backend.domain.chat.exception;

import com.teaching.backend.global.exception.GeneralException;

public class ChatException extends GeneralException {

    public ChatException(ChatErrorCode errorCode) {
        super(errorCode);
    }
}
