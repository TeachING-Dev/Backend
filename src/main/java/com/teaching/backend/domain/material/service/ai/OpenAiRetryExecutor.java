package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class OpenAiRetryExecutor {

    private static final int MAX_ATTEMPTS = 2;

    private final long backoffMillis;

    public OpenAiRetryExecutor(@Value("${openai.retry.backoff-ms:0}") long backoffMillis) {
        this.backoffMillis = Math.max(0L, backoffMillis);
    }

    public <T> T execute(Supplier<T> action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (!isRetryable(e) || attempt == MAX_ATTEMPTS) {
                    if (isRetryable(e)) {
                        throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
                    }
                    throw e;
                }
                lastFailure = e;
                sleepBeforeRetry();
            }
        }

        throw lastFailure == null
                ? new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED)
                : lastFailure;
    }

    private boolean isRetryable(RuntimeException e) {
        if (e instanceof GeneralException generalException) {
            return generalException.getErrorCode() == GlobalErrorCode.INTERNAL_SERVER_ERROR;
        }

        Throwable cause = e;
        while (cause != null) {
            String simpleName = cause.getClass().getSimpleName().toLowerCase();
            if (simpleName.contains("timeout") || simpleName.contains("connect")) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    private void sleepBeforeRetry() {
        if (backoffMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
        }
    }
}
