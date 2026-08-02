package com.teaching.backend.domain.notification.exception;

import com.teaching.backend.global.exception.GeneralException;

public class NotificationException extends GeneralException {

    public NotificationException(NotificationErrorCode errorCode) {
        super(errorCode);
    }
}
