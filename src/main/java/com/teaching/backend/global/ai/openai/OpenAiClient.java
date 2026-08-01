package com.teaching.backend.global.ai.openai;

import com.teaching.backend.global.ai.openai.dto.ChatCompletionRequest;
import com.teaching.backend.global.ai.openai.dto.ChatCompletionResponse;
import com.teaching.backend.global.ai.openai.dto.EmbeddingRequest;
import com.teaching.backend.global.ai.openai.dto.EmbeddingResponse;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

// OpenAI Embedding/Chat Completion API를 직접 호출하는 클라이언트 (Spring AI 미사용)
@Component
@Slf4j
public class OpenAiClient {

    private static final int ERROR_BODY_LOG_PREFIX_LENGTH = 500;

    private final WebClient webClient;
    private final String embeddingModel;
    private final String chatModel;
    private final Duration responseTimeout;

    public OpenAiClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url}") String baseUrl,
            @Value("${openai.embedding-model}") String embeddingModel,
            @Value("${openai.chat-model}") String chatModel,
            @Value("${openai.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${openai.response-timeout-ms:30000}") long responseTimeoutMs
    ) {
        this.responseTimeout = Duration.ofMillis(responseTimeoutMs);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(responseTimeout);

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
    }

    public float[] embed(String text) {
        long startedAt = System.nanoTime();
        EmbeddingResponse response = call("embedding", startedAt, webClient.post()
                .uri("/v1/embeddings")
                .bodyValue(new EmbeddingRequest(embeddingModel, text))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(EmbeddingResponse.class));

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }

        EmbeddingResponse.Data data = response.data().get(0);
        if (data == null || data.embedding() == null || data.embedding().length == 0) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
        return data.embedding();
    }

    public String chatComplete(String systemPrompt, String userMessage) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                chatModel,
                List.of(
                        new ChatCompletionRequest.Message("system", systemPrompt),
                        new ChatCompletionRequest.Message("user", userMessage)
                ),
                0.3
        );

        return requestChatCompletion(request);
    }

    // OpenAI가 순수 JSON 객체만 반환하도록 강제(response_format=json_object). 자료 AI 분석처럼
    // 응답을 그대로 파싱해 DB에 저장해야 하는 호출부에서 코드펜스/부연설명 혼입 리스크를 줄이기 위해 사용.
    public String chatCompleteJson(String systemPrompt, String userMessage) {
        ChatCompletionRequest request = ChatCompletionRequest.jsonMode(
                chatModel,
                List.of(
                        new ChatCompletionRequest.Message("system", systemPrompt),
                        new ChatCompletionRequest.Message("user", userMessage)
                ),
                0.3
        );

        return requestChatCompletion(request);
    }

    private String requestChatCompletion(ChatCompletionRequest request) {
        long startedAt = System.nanoTime();
        ChatCompletionResponse response = call("chat_completion", startedAt, webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(ChatCompletionResponse.class));

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            log.warn(
                    "OpenAI chat completion returned an empty response. elapsedMs={}",
                    elapsedMs(startedAt)
            );
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }

        ChatCompletionResponse.Choice choice = response.choices().get(0);
        ChatCompletionResponse.Choice.Message message = choice == null ? null : choice.message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            log.warn(
                    "OpenAI chat completion returned a blank message. elapsedMs={}",
                    elapsedMs(startedAt)
            );
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
        log.info(
                "OpenAI chat completion succeeded. elapsedMs={}, rawResponseLength={}",
                elapsedMs(startedAt),
                message.content().length()
        );
        return message.content();
    }

    private Mono<Throwable> mapError(ClientResponse response) {
        HttpStatusCode statusCode = response.statusCode();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(responseBody -> new OpenAiHttpException(statusCode, responseBody));
    }

    // OpenAI 응답 실패(HTTP 에러 상태, 네트워크 예외, 타임아웃)를 공통 에러 응답으로 변환.
    // WebClient의 responseTimeout은 커넥션 확보 이후 응답 지연만 커버하므로,
    // block(Duration)으로 전체 대기 시간의 상한을 별도로 강제해 MVC 요청 스레드가 무한정 잡히지 않게 한다.
    private <T> T call(String operation, long startedAt, Mono<T> mono) {
        try {
            return mono.block(responseTimeout);
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            OpenAiFailureDiagnostic diagnostic = diagnoseFailure(e);
            log.warn(
                    "OpenAI request failed. operation={}, elapsedMs={}, exceptionClass={}, rootCauseClass={}, rootCauseMessage={}, httpStatus={}, timeout={}, dataBufferLimit={}, errorBodyPrefix={}",
                    operation,
                    elapsedMs(startedAt),
                    diagnostic.exceptionClass(),
                    diagnostic.rootCauseClass(),
                    diagnostic.rootCauseMessage(),
                    diagnostic.httpStatus(),
                    diagnostic.timeout(),
                    diagnostic.dataBufferLimit(),
                    diagnostic.errorBodyPrefix(),
                    e
            );
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    static OpenAiFailureDiagnostic diagnoseFailure(Throwable failure) {
        Throwable rootCause = rootCause(failure);
        OpenAiHttpException openAiHttpException = findCause(failure, OpenAiHttpException.class);
        WebClientResponseException webClientResponseException = findCause(failure, WebClientResponseException.class);
        HttpStatusCode httpStatus = null;
        String errorBody = null;
        if (openAiHttpException != null) {
            httpStatus = openAiHttpException.statusCode();
            errorBody = openAiHttpException.responseBody();
        } else if (webClientResponseException != null) {
            httpStatus = webClientResponseException.getStatusCode();
            errorBody = webClientResponseException.getResponseBodyAsString(StandardCharsets.UTF_8);
        }

        return new OpenAiFailureDiagnostic(
                simpleName(failure),
                simpleName(rootCause),
                safeMessage(rootCause),
                httpStatus == null ? null : httpStatus.toString(),
                isTimeout(failure),
                findCause(failure, DataBufferLimitException.class) != null,
                safePrefix(errorBody, ERROR_BODY_LOG_PREFIX_LENGTH)
        );
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private static Throwable rootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                return null;
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            String simpleName = current.getClass().getSimpleName();
            if (simpleName != null && simpleName.toLowerCase().contains("timeout")) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String simpleName(Throwable throwable) {
        return throwable == null ? null : throwable.getClass().getSimpleName();
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return null;
        }
        return safePrefix(throwable.getMessage(), ERROR_BODY_LOG_PREFIX_LENGTH);
    }

    static String safePrefix(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    record OpenAiFailureDiagnostic(
            String exceptionClass,
            String rootCauseClass,
            String rootCauseMessage,
            String httpStatus,
            boolean timeout,
            boolean dataBufferLimit,
            String errorBodyPrefix
    ) {
    }

    private static final class OpenAiHttpException extends RuntimeException {

        private final HttpStatusCode statusCode;
        private final String responseBody;

        private OpenAiHttpException(HttpStatusCode statusCode, String responseBody) {
            super("OpenAI HTTP error: " + statusCode);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        private HttpStatusCode statusCode() {
            return statusCode;
        }

        private String responseBody() {
            return responseBody;
        }
    }
}
