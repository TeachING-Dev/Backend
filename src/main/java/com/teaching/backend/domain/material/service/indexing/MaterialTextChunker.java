package com.teaching.backend.domain.material.service.indexing;

import com.teaching.backend.domain.material.dto.indexing.MaterialTextChunk;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
                chunks.add(new MaterialTextChunk(chunkIndex++, chunkText, "청크 " + chunkIndex));
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
