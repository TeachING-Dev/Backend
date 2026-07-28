package com.teaching.backend.domain.material.service.indexing;

import com.teaching.backend.domain.material.dto.indexing.MaterialTextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialTextChunkerTest {

    @Test
    void splitsTextIntoChunksWithOverlap() {
        MaterialTextChunker chunker = new MaterialTextChunker(10, 2);

        List<MaterialTextChunk> chunks = chunker.chunk("abcdefghij12345");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(0).text()).isEqualTo("abcdefghij");
        assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);
        assertThat(chunks.get(1).text()).startsWith("ij");
    }

    @Test
    void shortTextCreatesSingleChunk() {
        MaterialTextChunker chunker = new MaterialTextChunker(100, 10);

        List<MaterialTextChunk> chunks = chunker.chunk("short text");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("short text");
    }

    @Test
    void blankAndNullTextCreateNoChunks() {
        MaterialTextChunker chunker = new MaterialTextChunker(100, 10);

        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   ")).isEmpty();
    }

    @Test
    void normalizesWhitespaceAndKeepsKoreanText() {
        MaterialTextChunker chunker = new MaterialTextChunker(100, 10);

        List<MaterialTextChunk> chunks = chunker.chunk("  첫 문장입니다.\r\n\r\n   두 번째   문장입니다.  ");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("첫 문장입니다.");
        assertThat(chunks.get(0).text()).contains("두 번째 문장입니다.");
    }

    @Test
    void includesLastChunkWithoutInfiniteLoop() {
        MaterialTextChunker chunker = new MaterialTextChunker(5, 1);

        List<MaterialTextChunk> chunks = chunker.chunk("abcdefghijkl");

        assertThat(chunks).extracting(MaterialTextChunk::chunkIndex)
                .containsExactly(0, 1, 2);
        assertThat(chunks.get(2).text()).isEqualTo("ijkl");
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new MaterialTextChunker(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MaterialTextChunker(10, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MaterialTextChunker(10, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
