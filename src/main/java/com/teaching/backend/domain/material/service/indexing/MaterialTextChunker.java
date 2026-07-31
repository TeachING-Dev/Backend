package com.teaching.backend.domain.material.service.indexing;

import com.teaching.backend.domain.material.dto.indexing.MaterialTextChunk;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Component
public class MaterialTextChunker {

    private static final int MIN_BREAK_DISTANCE = 100;

    private final int chunkSize;
    private final int chunkOverlap;

    public MaterialTextChunker(
            @Value("${material.indexing.chunk-size:900}") int chunkSize,
            @Value("${material.indexing.chunk-overlap:150}") int chunkOverlap
    ) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be greater than or equal to 0 and less than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<MaterialTextChunk> chunk(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        int[] lineStarts = computeLineStarts(normalized);

        List<MaterialTextChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            if (end < normalized.length()) {
                end = preferredEnd(normalized, start, end);
            }

            String chunkText = normalized.substring(start, end).strip();
            if (!chunkText.isBlank()) {
                int startLine = lineNumberAt(lineStarts, start);
                int endLine = lineNumberAt(lineStarts, Math.max(start, end - 1));
                chunks.add(new MaterialTextChunk(chunkIndex++, chunkText, startLine, endLine));
            }

            if (end >= normalized.length()) {
                break;
            }

            int nextStart = Math.max(0, end - chunkOverlap);
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return chunks;
    }

    // normalized 텍스트에서 각 줄이 시작하는 문자 오프셋 목록 (1번째 줄은 항상 0부터 시작)
    private int[] computeLineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        int[] result = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) {
            result[i] = starts.get(i);
        }
        return result;
    }

    // 문자 오프셋이 몇 번째 줄(1-based)에 속하는지 계산
    private int lineNumberAt(int[] lineStarts, int offset) {
        int index = Arrays.binarySearch(lineStarts, offset);
        if (index < 0) {
            index = -index - 2;
        }
        return index + 1;
    }

    private int preferredEnd(String text, int start, int hardEnd) {
        int minEnd = Math.min(hardEnd, start + MIN_BREAK_DISTANCE);
        int paragraphBreak = text.lastIndexOf("\n\n", hardEnd);
        if (paragraphBreak >= minEnd) {
            return paragraphBreak;
        }

        int sentenceBreak = Math.max(
                text.lastIndexOf(". ", hardEnd),
                text.lastIndexOf("다. ", hardEnd)
        );
        if (sentenceBreak >= minEnd) {
            return sentenceBreak + 1;
        }

        int whitespaceBreak = text.lastIndexOf(' ', hardEnd);
        if (whitespaceBreak >= minEnd) {
            return whitespaceBreak;
        }

        return hardEnd;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ");
        String[] lines = normalized.split("\\n", -1);
        List<String> trimmedLines = new ArrayList<>();
        for (String line : lines) {
            trimmedLines.add(line.strip());
        }
        return String.join("\n", trimmedLines)
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
