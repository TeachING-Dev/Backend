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
    void computesStartAndEndLineWithinChunk() {
        MaterialTextChunker chunker = new MaterialTextChunker(100, 10);

        List<MaterialTextChunk> chunks = chunker.chunk("첫째 줄\n둘째 줄\n셋째 줄");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).startLine()).isEqualTo(1);
        assertThat(chunks.get(0).endLine()).isEqualTo(3);
    }

    @Test
    void splitAcrossLinesReportsDistinctStartAndEndLinePerChunk() {
        MaterialTextChunker chunker = new MaterialTextChunker(5, 1);

        List<MaterialTextChunk> chunks = chunker.chunk("ab\ncd\nef\ngh");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).startLine()).isEqualTo(1);
        assertThat(chunks.get(chunks.size() - 1).endLine()).isEqualTo(4);
    }

    @Test
    void leadingBlankLinesAreExcludedFromOriginalLineNumbers() {
        MaterialTextChunker chunker = new MaterialTextChunker(100, 10);

        // 원문 1~2행은 빈 줄이라 정규화 후 삭제되므로, "첫 줄"은 정규화 텍스트에서는 1행이지만
        // 원문 기준으로는 3행이어야 한다.
        List<MaterialTextChunk> chunks = chunker.chunk("\n\n첫 줄\n둘째 줄");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).startLine()).isEqualTo(3);
        assertThat(chunks.get(0).endLine()).isEqualTo(4);
    }

    @Test
    void threeOrMoreBlankLinesCollapseButKeepOriginalLineNumbers() {
        MaterialTextChunker chunker = new MaterialTextChunker(100, 10);

        // 원문에서 "ab"와 "cd" 사이에 빈 줄이 3개(2~4행) 있어 정규화 시 1개로 축약되지만,
        // "cd"의 endLine은 정규화 텍스트 기준 3행이 아니라 원문 기준 5행이어야 한다.
        List<MaterialTextChunk> chunks = chunker.chunk("ab\n\n\n\ncd");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).startLine()).isEqualTo(1);
        assertThat(chunks.get(0).endLine()).isEqualTo(5);
    }

    @Test
    void chunkLineNumbersIgnoreStrippedLeadingWhitespaceOffsets() {
        MaterialTextChunker chunker = new MaterialTextChunker(3, 1);

        // 두 번째 청크의 원본 슬라이스는 "\n\nc"이고 strip() 후 본문은 "c"뿐이다.
        // strip 이전 offset으로 줄 번호를 매기면 잘려나간 개행이 속한 1행("ab")으로 잘못 계산되므로,
        // 실제 본문("c")이 시작하는 원문 5행이 나와야 한다.
        List<MaterialTextChunk> chunks = chunker.chunk("ab\n\n\n\ncd");

        MaterialTextChunk chunkAfterBlankRun = chunks.get(1);
        assertThat(chunkAfterBlankRun.text()).isEqualTo("c");
        assertThat(chunkAfterBlankRun.startLine()).isEqualTo(5);
        assertThat(chunkAfterBlankRun.endLine()).isEqualTo(5);
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
