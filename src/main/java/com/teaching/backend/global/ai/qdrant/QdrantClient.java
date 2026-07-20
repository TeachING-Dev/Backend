package com.teaching.backend.global.ai.qdrant;

import com.teaching.backend.global.ai.qdrant.dto.CollectionInfoResponse;
import com.teaching.backend.global.ai.qdrant.dto.CreateCollectionRequest;
import com.teaching.backend.global.ai.qdrant.dto.DeletePointsRequest;
import com.teaching.backend.global.ai.qdrant.dto.QdrantSearchHit;
import com.teaching.backend.global.ai.qdrant.dto.QdrantSearchResponse;
import com.teaching.backend.global.ai.qdrant.dto.SearchRequest;
import com.teaching.backend.global.ai.qdrant.dto.UpsertPointsRequest;
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
import java.util.Map;

// Qdrant 벡터 DB REST API를 직접 호출하는 클라이언트 (Spring AI 미사용)
@Component
public class QdrantClient {

    private final WebClient webClient;
    private final String collectionName;
    private final int vectorSize;
    private final Duration responseTimeout;

    public QdrantClient(
            @Value("${qdrant.url}") String url,
            @Value("${qdrant.api-key:}") String apiKey,
            @Value("${qdrant.collection-name}") String collectionName,
            @Value("${qdrant.vector-size}") int vectorSize,
            @Value("${qdrant.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${qdrant.response-timeout-ms:30000}") long responseTimeoutMs
    ) {
        this.responseTimeout = Duration.ofMillis(responseTimeoutMs);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(responseTimeout);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(url)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("api-key", apiKey);
        }
        this.webClient = builder.build();
        this.collectionName = collectionName;
        this.vectorSize = vectorSize;
    }

    // 컬렉션이 없으면 Cosine distance, vectorSize 차원으로 생성 (idempotent).
    // 이미 존재하면 임베딩 모델 교체 등으로 설정된 vectorSize와 실제 컬렉션 차원이 어긋나지 않았는지 검증.
    public void ensureCollection() {
        CollectionInfoResponse info = call(webClient.get()
                .uri("/collections/{name}", collectionName)
                .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                        ? response.bodyToMono(CollectionInfoResponse.class)
                        : response.releaseBody().then(Mono.<CollectionInfoResponse>empty())));

        if (info != null) {
            validateVectorSize(info);
            return;
        }

        call(webClient.put()
                .uri("/collections/{name}", collectionName)
                .bodyValue(new CreateCollectionRequest(new CreateCollectionRequest.VectorParams(vectorSize, "Cosine")))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(Void.class));
    }

    // 기존 컬렉션의 벡터 차원이 qdrant.vector-size 설정과 다르면 임베딩 upsert 시점의 불명확한 실패 대신 즉시 실패시킴
    private void validateVectorSize(CollectionInfoResponse info) {
        Integer existingSize = info.result() == null || info.result().config() == null
                || info.result().config().params() == null || info.result().config().params().vectors() == null
                ? null
                : info.result().config().params().vectors().size();

        if (existingSize == null || !existingSize.equals(vectorSize)) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public void upsertPoint(String pointId, float[] vector, Map<String, Object> payload) {
        UpsertPointsRequest request = new UpsertPointsRequest(
                List.of(new UpsertPointsRequest.Point(pointId, vector, payload))
        );

        call(webClient.put()
                .uri("/collections/{name}/points?wait=true", collectionName)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(Void.class));
    }

    // 색인 도중 실패 시 이미 upsert된 포인트를 되돌리기 위한 보상용 삭제
    public void deletePoints(List<String> pointIds) {
        if (pointIds.isEmpty()) {
            return;
        }

        call(webClient.post()
                .uri("/collections/{name}/points/delete?wait=true", collectionName)
                .bodyValue(new DeletePointsRequest(pointIds))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(Void.class));
    }

    // userId로 필터링해서 요청자 소유 자료의 청크만 검색되도록 강제
    public List<QdrantSearchHit> search(float[] vector, int topK, Long userId) {
        SearchRequest request = new SearchRequest(
                vector,
                topK,
                new SearchRequest.Filter(List.of(
                        new SearchRequest.Filter.Condition(
                                "userId",
                                new SearchRequest.Filter.Condition.Match(userId)
                        )
                )),
                true
        );

        QdrantSearchResponse response = call(webClient.post()
                .uri("/collections/{name}/points/search", collectionName)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(QdrantSearchResponse.class));

        return response == null || response.result() == null ? List.of() : response.result();
    }

    private Mono<Throwable> mapError(ClientResponse response) {
        return Mono.error(new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR));
    }

    // Qdrant 응답 실패(HTTP 에러 상태, 네트워크 예외, 타임아웃)를 공통 에러 응답으로 변환.
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
