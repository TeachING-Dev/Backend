package com.teaching.backend.global.ai.qdrant.dto;

public record CreateCollectionRequest(VectorParams vectors) {

    public record VectorParams(int size, String distance) {
    }
}
