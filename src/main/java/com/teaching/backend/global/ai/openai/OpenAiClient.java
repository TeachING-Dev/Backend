package com.teaching.backend.global.ai.openai;

import com.teaching.backend.global.ai.openai.dto.ChatCompletionRequest;
import com.teaching.backend.global.ai.openai.dto.ChatCompletionResponse;
import com.teaching.backend.global.ai.openai.dto.EmbeddingRequest;
import com.teaching.backend.global.ai.openai.dto.EmbeddingResponse;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;

// OpenAI Embedding/Chat Completion API를 직접 호출하는 클라이언트 (Spring AI 미사용)
@Component
public class OpenAiClient {

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
        EmbeddingResponse response = call(webClient.post()
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
        ChatCompletionResponse response = call(webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(ChatCompletionResponse.class));

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }

        ChatCompletionResponse.Choice choice = response.choices().get(0);
        ChatCompletionResponse.Choice.Message message = choice == null ? null : choice.message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
        return message.content();
    }

    private Mono<Throwable> mapError(ClientResponse response) {
        return Mono.error(new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR));
    }

    // OpenAI 응답 실패(HTTP 에러 상태, 네트워크 예외, 타임아웃)를 공통 에러 응답으로 변환.
    // WebClient의 responseTimeout은 커넥션 확보 이후 응답 지연만 커버하므로,
    // block(Duration)으로 전체 대기 시간의 상한을 별도로 강제해 MVC 요청 스레드가 무한정 잡히지 않게 한다.
    private <T> T call(Mono<T> mono) {
        try {
            return mono.block(responseTimeout);
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
