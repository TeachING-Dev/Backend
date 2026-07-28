package com.teaching.backend.domain.material.dto.indexing;

public record MaterialTextChunk(
        int chunkIndex,
        String text,
        String position
) {
}
