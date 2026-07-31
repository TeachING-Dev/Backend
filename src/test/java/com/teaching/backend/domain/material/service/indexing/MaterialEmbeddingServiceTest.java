package com.teaching.backend.domain.material.service.indexing;

import com.teaching.backend.domain.material.dto.indexing.EmbeddedMaterialTextChunk;
import com.teaching.backend.domain.material.dto.indexing.MaterialTextChunk;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaterialEmbeddingServiceTest {

    private final OpenAiClient openAiClient = mock(OpenAiClient.class);
    private final MaterialEmbeddingService embeddingService = new MaterialEmbeddingService(openAiClient);

    @Test
    void embedsChunksInOrder() {
        MaterialTextChunk first = new MaterialTextChunk(0, "first", 1, 1);
        MaterialTextChunk second = new MaterialTextChunk(1, "second", 2, 2);
        when(openAiClient.embed("first")).thenReturn(new float[]{0.1f});
        when(openAiClient.embed("second")).thenReturn(new float[]{0.2f});

        List<EmbeddedMaterialTextChunk> result = embeddingService.embedChunks(List.of(first, second));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).chunk()).isEqualTo(first);
        assertThat(result.get(0).vector()).containsExactly(0.1f);
        assertThat(result.get(1).chunk()).isEqualTo(second);
        assertThat(result.get(1).vector()).containsExactly(0.2f);
    }

    @Test
    void skipsBlankChunksWithoutApiCall() {
        MaterialTextChunk blank = new MaterialTextChunk(0, " ", 1, 1);

        assertThat(embeddingService.embedChunks(List.of(blank))).isEmpty();
        verify(openAiClient, never()).embed(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void convertsExternalClientFailure() {
        RuntimeException cause = new RuntimeException("network");
        when(openAiClient.embed("first")).thenThrow(cause);

        assertThatThrownBy(() -> embeddingService.embedChunks(List.of(new MaterialTextChunk(0, "first", 1, 1))))
                .isInstanceOf(MaterialException.class)
                .satisfies(exception -> {
                    MaterialException materialException = (MaterialException) exception;
                    assertThat(materialException.getErrorCode()).isEqualTo(MaterialErrorCode.MATERIAL_EMBEDDING_FAILED);
                    assertThat(materialException.getCause()).isSameAs(cause);
                });
    }
}
