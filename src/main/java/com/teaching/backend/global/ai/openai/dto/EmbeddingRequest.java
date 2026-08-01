package com.teaching.backend.global.ai.openai.dto;

// input은 OpenAI embeddings API 규격상 단일 문자열 또는 문자열 배열(batch) 둘 다 허용된다.
public record EmbeddingRequest(String model, Object input) {
}
