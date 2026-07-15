package com.teaching.backend.global.ai.openai;

import com.teaching.backend.global.ai.openai.dto.ChatCompletionRequest;
import com.teaching.backend.global.ai.openai.dto.ChatCompletionResponse;
import com.teaching.backend.global.ai.openai.dto.EmbeddingRequest;
import com.teaching.backend.global.ai.openai.dto.EmbeddingResponse;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

// OpenAI Embedding/Chat Completion API를 직접 호출하는 클라이언트 (Spring AI 미사용)
@Component
public class OpenAiClient {

    private final WebClient webClient;
    private final String embeddingModel;
    private final String chatModel;

    public OpenAiClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url}") String baseUrl,
            @Value("${openai.embedding-model}") String embeddingModel,
            @Value("${openai.chat-model}") String chatModel
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
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

        if (response == null || response.data().isEmpty()) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
        return response.data().get(0).embedding();
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

        ChatCompletionResponse response = call(webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(ChatCompletionResponse.class));

        if (response == null || response.choices().isEmpty()) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
        return response.choices().get(0).message().content();
    }

    private Mono<Throwable> mapError(ClientResponse response) {
        return Mono.error(new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR));
    }

    // OpenAI 응답 실패(HTTP 에러 상태, 네트워크 예외)를 공통 에러 응답으로 변환
    private <T> T call(Mono<T> mono) {
        try {
            return mono.block();
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
