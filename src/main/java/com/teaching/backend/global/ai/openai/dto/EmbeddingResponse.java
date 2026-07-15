package com.teaching.backend.global.ai.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbeddingResponse(List<Data> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(float[] embedding) {
    }
}
