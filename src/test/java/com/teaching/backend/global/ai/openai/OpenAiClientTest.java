package com.teaching.backend.global.ai.openai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientTest {

    @Test
    void diagnoseFailureExtractsHttpStatusAndBodyPrefixFromWebClientResponseException() {
        WebClientResponseException exception = WebClientResponseException.create(
                400,
                "Bad Request",
                HttpHeaders.EMPTY,
                "{\"error\":{\"message\":\"context length exceeded\"}}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        OpenAiClient.OpenAiFailureDiagnostic diagnostic = OpenAiClient.diagnoseFailure(exception);

        assertThat(diagnostic.exceptionClass()).contains("BadRequest");
        assertThat(diagnostic.rootCauseClass()).contains("BadRequest");
        assertThat(diagnostic.httpStatus()).contains("400");
        assertThat(diagnostic.errorBodyPrefix()).contains("context length exceeded");
        assertThat(diagnostic.timeout()).isFalse();
        assertThat(diagnostic.dataBufferLimit()).isFalse();
    }

    @Test
    void diagnoseFailureDetectsTimeoutCause() {
        RuntimeException exception = new RuntimeException(new TimeoutException("response timeout"));

        OpenAiClient.OpenAiFailureDiagnostic diagnostic = OpenAiClient.diagnoseFailure(exception);

        assertThat(diagnostic.exceptionClass()).isEqualTo("RuntimeException");
        assertThat(diagnostic.rootCauseClass()).isEqualTo("TimeoutException");
        assertThat(diagnostic.rootCauseMessage()).contains("response timeout");
        assertThat(diagnostic.timeout()).isTrue();
        assertThat(diagnostic.dataBufferLimit()).isFalse();
    }

    @Test
    void diagnoseFailureDetectsDataBufferLimitCause() {
        RuntimeException exception = new RuntimeException(new DataBufferLimitException("Exceeded limit"));

        OpenAiClient.OpenAiFailureDiagnostic diagnostic = OpenAiClient.diagnoseFailure(exception);

        assertThat(diagnostic.rootCauseClass()).isEqualTo("DataBufferLimitException");
        assertThat(diagnostic.dataBufferLimit()).isTrue();
        assertThat(diagnostic.timeout()).isFalse();
    }

    @Test
    void diagnoseFailurePreservesGeneralRootCause() {
        RuntimeException exception = new RuntimeException(new IllegalStateException("network unavailable"));

        OpenAiClient.OpenAiFailureDiagnostic diagnostic = OpenAiClient.diagnoseFailure(exception);

        assertThat(diagnostic.exceptionClass()).isEqualTo("RuntimeException");
        assertThat(diagnostic.rootCauseClass()).isEqualTo("IllegalStateException");
        assertThat(diagnostic.rootCauseMessage()).contains("network unavailable");
        assertThat(diagnostic.httpStatus()).isNull();
    }

    @Test
    void safePrefixNormalizesLineBreaksAndTruncates() {
        String prefix = OpenAiClient.safePrefix(" first line\nsecond line\tthird ", 18);

        assertThat(prefix).isEqualTo("first line second ");
    }
}
