package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;

class HtmlFetchException extends MaterialException {

    private final boolean renderedFallbackAllowed;

    HtmlFetchException(MaterialErrorCode errorCode, boolean renderedFallbackAllowed) {
        super(errorCode);
        this.renderedFallbackAllowed = renderedFallbackAllowed;
    }

    HtmlFetchException(
            MaterialErrorCode errorCode,
            Throwable cause,
            boolean renderedFallbackAllowed
    ) {
        super(errorCode, cause);
        this.renderedFallbackAllowed = renderedFallbackAllowed;
    }

    boolean isRenderedFallbackAllowed() {
        return renderedFallbackAllowed;
    }
}
