package com.teaching.backend.global.ai.qdrant;

import com.teaching.backend.global.ai.qdrant.dto.CreateCollectionRequest;
import com.teaching.backend.global.ai.qdrant.dto.DeletePointsRequest;
import com.teaching.backend.global.ai.qdrant.dto.QdrantSearchHit;
import com.teaching.backend.global.ai.qdrant.dto.QdrantSearchResponse;
import com.teaching.backend.global.ai.qdrant.dto.SearchRequest;
import com.teaching.backend.global.ai.qdrant.dto.UpsertPointsRequest;
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
import java.util.Map;

// Qdrant 벡터 DB REST API를 직접 호출하는 클라이언트 (Spring AI 미사용)
@Component
public class QdrantClient {

    private final WebClient webClient;
    private final String collectionName;
    private final int vectorSize;

    public QdrantClient(
            @Value("${qdrant.url}") String url,
            @Value("${qdrant.api-key:}") String apiKey,
            @Value("${qdrant.collection-name}") String collectionName,
            @Value("${qdrant.vector-size}") int vectorSize
    ) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("api-key", apiKey);
        }
        this.webClient = builder.build();
        this.collectionName = collectionName;
        this.vectorSize = vectorSize;
    }

    // 컬렉션이 없으면 Cosine distance, vectorSize 차원으로 생성 (idempotent)
    public void ensureCollection() {
        boolean exists = call(webClient.get()
                .uri("/collections/{name}", collectionName)
                .exchangeToMono(response -> response.releaseBody()
                        .thenReturn(response.statusCode().is2xxSuccessful())));

        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        call(webClient.put()
                .uri("/collections/{name}", collectionName)
                .bodyValue(new CreateCollectionRequest(new CreateCollectionRequest.VectorParams(vectorSize, "Cosine")))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(Void.class));
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

    // Qdrant 응답 실패(HTTP 에러 상태, 네트워크 예외)를 공통 에러 응답으로 변환
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
