package com.teaching.backend.domain.material.dto.indexing;

public record EmbeddedMaterialTextChunk(
        MaterialTextChunk chunk,
        float[] vector
) {
}
