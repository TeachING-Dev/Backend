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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// OpenAI Embedding/Chat Completion API를 직접 호출하는 클라이언트 (Spring AI 미사용)
@Slf4j
@Component
public class OpenAiClient {

    // 자료 개수가 많은 유저는 한 번에 보낼 텍스트가 많아져 OpenAI 배치 한도/응답 크기가 계속 커질 수 있으므로,
    // embedBatch()에서 이 크기로 나눠 순차 호출한다.
    private static final int EMBED_BATCH_SIZE = 100;

    private static final ObjectMapper ERROR_BODY_MAPPER = new ObjectMapper();

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

        // 기본 256KB 버퍼 한도로는 자료가 많은 유저의 title/tag 배치 임베딩(embedBatch) 응답이
        // 잘려서 DataBufferLimitException이 나기 때문에, 임베딩 응답 크기에 맞춰 여유 있게 올려둔다.
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(exchangeStrategies)
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

    // 여러 텍스트를 임베딩한다. OpenAI 응답은 입력 순서를 그대로 보존한다.
    // chunk가 여러 개여도 전체 처리 시간이 (chunk 수 x 단건 타임아웃)을 넘지 않도록 하나의 마감 시간을 공유한다.
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        int chunkCount = (texts.size() + EMBED_BATCH_SIZE - 1) / EMBED_BATCH_SIZE;
        Instant deadline = Instant.now().plus(responseTimeout.multipliedBy(chunkCount));

        List<float[]> embeddings = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += EMBED_BATCH_SIZE) {
            int end = Math.min(start + EMBED_BATCH_SIZE, texts.size());
            embeddings.addAll(embedBatchChunk(texts.subList(start, end), remainingTimeout(deadline)));
        }
        return embeddings;
    }

    // 이미 마감 시간을 넘겼으면 다음 chunk 요청을 보내지 않고 즉시 실패시켜, 앞 chunk들이 지연된 만큼
    // 뒤 chunk에게 남은 시간만 준다.
    private Duration remainingTimeout(Instant deadline) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            log.warn("OpenAI embedBatch exceeded overall timeout budget");
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
        return remaining;
    }

    private List<float[]> embedBatchChunk(List<String> texts, Duration timeout) {
        EmbeddingResponse response = call(webClient.post()
                .uri("/v1/embeddings")
                .bodyValue(new EmbeddingRequest(embeddingModel, texts))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(EmbeddingResponse.class), timeout);

        if (response == null || response.data() == null || response.data().size() != texts.size()) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 응답은 입력 순서대로 오는 게 보장되지만, index를 명시적으로 검증/매핑해 순서에 대한
        // 암묵적 가정 없이 원래 입력 순서로 안전하게 정렬한다.
        float[][] embeddingsByIndex = new float[texts.size()][];
        for (EmbeddingResponse.Data data : response.data()) {
            if (data == null || data.index() < 0 || data.index() >= texts.size() || embeddingsByIndex[data.index()] != null) {
                throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
            }
            embeddingsByIndex[data.index()] = data.embedding();
        }
        return List.of(embeddingsByIndex);
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
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    log.warn("OpenAI request failed. status={}, error={}", response.statusCode().value(), summarize(errorSummary(body)));
                    return new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
                });
    }

    // 응답 본문의 error.message에는 사용자가 보낸 질문/자료 원문 일부가 그대로 포함될 수 있어(예: 콘텐츠
    // 정책 위반 시 문제 텍스트를 인용), 로그에는 원인 파악에 필요한 type/code만 남기고 원문은 남기지 않는다.
    private String errorSummary(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        try {
            JsonNode error = ERROR_BODY_MAPPER.readTree(body).path("error");
            String type = error.path("type").asText("unknown");
            String code = error.path("code").asText("unknown");
            return "type=%s, code=%s".formatted(type, code);
        } catch (Exception e) {
            return "<unparseable>";
        }
    }

    private String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String normalized = body.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    // OpenAI 응답 실패(HTTP 에러 상태, 네트워크 예외, 타임아웃)를 공통 에러 응답으로 변환.
    // WebClient의 responseTimeout은 커넥션 확보 이후 응답 지연만 커버하므로,
    // block(Duration)으로 전체 대기 시간의 상한을 별도로 강제해 MVC 요청 스레드가 무한정 잡히지 않게 한다.
    private <T> T call(Mono<T> mono) {
        return call(mono, responseTimeout);
    }

    private <T> T call(Mono<T> mono, Duration timeout) {
        try {
            return mono.block(timeout);
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OpenAI request failed. reason={}, message={}", e.getClass().getSimpleName(), e.getMessage());
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
