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
        NormalizedText normalized = normalize(text);
        String normalizedText = normalized.text();
        if (normalizedText.isBlank()) {
            return List.of();
        }

        int[] lineStarts = computeLineStarts(normalizedText);

        List<MaterialTextChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;
        while (start < normalizedText.length()) {
            int end = Math.min(start + chunkSize, normalizedText.length());
            if (end < normalizedText.length()) {
                end = preferredEnd(normalizedText, start, end);
            }

            // strip() 전 start/end로 줄 번호를 매기면 잘려나간 공백/개행이 속한 줄이 잘못 포함되므로,
            // 실제 본문이 시작/종료하는 오프셋을 먼저 구한 뒤 그 오프셋으로 줄 번호를 계산한다.
            int contentStart = start;
            while (contentStart < end && Character.isWhitespace(normalizedText.charAt(contentStart))) {
                contentStart++;
            }
            int contentEnd = end;
            while (contentEnd > contentStart && Character.isWhitespace(normalizedText.charAt(contentEnd - 1))) {
                contentEnd--;
            }
            String chunkText = normalizedText.substring(contentStart, contentEnd);
            if (!chunkText.isBlank()) {
                int startLine = originalLineAt(normalized, lineStarts, contentStart);
                int endLine = originalLineAt(normalized, lineStarts, contentEnd - 1);
                chunks.add(new MaterialTextChunk(chunkIndex++, chunkText, startLine, endLine));
            }

            if (end >= normalizedText.length()) {
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

    // normalized 텍스트의 문자 오프셋을 원문(정규화 전) 기준 1-based 줄 번호로 변환
    private int originalLineAt(NormalizedText normalized, int[] lineStarts, int offset) {
        int index = Arrays.binarySearch(lineStarts, offset);
        if (index < 0) {
            index = -index - 2;
        }
        return normalized.lineToOriginalLine()[index];
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

    // 원문을 정규화(개행 통일, 공백 정리, 빈 줄 축약)하면서 각 결과 줄이 원문의 몇 번째 줄이었는지 함께 기록한다.
    // 정규화 과정에서 선행 빈 줄은 통째로 삭제되고, 연속된 빈 줄은 1줄로 축약되어 원문과 줄 번호가 어긋나므로
    // startLine/endLine을 원문 기준으로 정확히 계산하려면 이 매핑이 반드시 필요하다.
    private NormalizedText normalize(String text) {
        if (text == null) {
            return new NormalizedText("", new int[0]);
        }

        String unified = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ");
        String[] rawLines = unified.split("\n", -1);

        List<String> resultLines = new ArrayList<>();
        List<Integer> lineToOriginalLine = new ArrayList<>();
        int blankRun = 0;
        for (int i = 0; i < rawLines.length; i++) {
            String stripped = rawLines[i].strip();
            if (stripped.isEmpty()) {
                blankRun++;
                continue;
            }

            // 선행 빈 줄(resultLines가 아직 비어있음)은 버리고, 그 외 연속된 빈 줄은 최대 1줄로만 남긴다.
            if (!resultLines.isEmpty() && blankRun > 0) {
                resultLines.add("");
                lineToOriginalLine.add(i); // 축약된 빈 줄 구간의 마지막 원문 줄 번호(1-based)
            }
            resultLines.add(stripped);
            lineToOriginalLine.add(i + 1);
            blankRun = 0;
        }

        String normalizedText = String.join("\n", resultLines);
        int[] originalLines = lineToOriginalLine.stream().mapToInt(Integer::intValue).toArray();
        return new NormalizedText(normalizedText, originalLines);
    }

    // normalize() 결과 텍스트와, 그 텍스트의 각 줄(0-based)이 원문의 몇 번째 줄(1-based)이었는지 매핑
    private record NormalizedText(String text, int[] lineToOriginalLine) {
    }
}
