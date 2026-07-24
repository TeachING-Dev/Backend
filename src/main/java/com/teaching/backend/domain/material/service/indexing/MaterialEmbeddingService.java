package com.teaching.backend.domain.material.service.indexing;

import com.teaching.backend.domain.material.dto.indexing.EmbeddedMaterialTextChunk;
import com.teaching.backend.domain.material.dto.indexing.MaterialTextChunk;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialEmbeddingService {

    private final OpenAiClient openAiClient;

    public List<EmbeddedMaterialTextChunk> embedChunks(List<MaterialTextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<EmbeddedMaterialTextChunk> embeddedChunks = new ArrayList<>();
        for (MaterialTextChunk chunk : chunks) {
            if (chunk == null || chunk.text() == null || chunk.text().isBlank()) {
                continue;
            }
            try {
                embeddedChunks.add(new EmbeddedMaterialTextChunk(chunk, openAiClient.embed(chunk.text())));
            } catch (RuntimeException e) {
                throw new MaterialException(MaterialErrorCode.MATERIAL_EMBEDDING_FAILED);
            }
        }

        if (embeddedChunks.size() != chunks.stream().filter(chunk ->
                chunk != null && chunk.text() != null && !chunk.text().isBlank()).count()) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_EMBEDDING_FAILED);
        }
        return embeddedChunks;
    }
}
