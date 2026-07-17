package com.teaching.backend.global.ai.qdrant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// GET /collections/{name} 응답 중 기존 컬렉션의 벡터 차원 검증에 필요한 필드만 추림
@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionInfoResponse(Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(Config config) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Config(Params params) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Params(VectorsConfig vectors) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VectorsConfig(Integer size) {
    }
}
