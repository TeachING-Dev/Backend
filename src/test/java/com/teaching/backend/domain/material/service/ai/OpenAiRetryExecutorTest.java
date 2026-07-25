package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiRetryExecutorTest {

    private final OpenAiRetryExecutor retryExecutor = new OpenAiRetryExecutor(0);

    @Test
    void returnsWhenFirstCallSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = retryExecutor.execute(() -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesInternalServerErrorOnceAndReturnsSecondResult() {
        AtomicInteger calls = new AtomicInteger();

        String result = retryExecutor.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void failsAfterTwoTransientFailures() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            calls.incrementAndGet();
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
        assertThat(calls).hasValue(2);
    }

    @Test
    void retriesTimeoutCauseOnceAndReturnsSecondResult() {
        AtomicInteger calls = new AtomicInteger();

        String result = retryExecutor.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new RuntimeException(new SocketTimeoutException("timeout"));
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotRetryBadRequest() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            calls.incrementAndGet();
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.BAD_REQUEST);
        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotRetryAuthenticationError() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            calls.incrementAndGet();
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
        assertThat(calls).hasValue(1);
    }
}
