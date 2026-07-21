package com.teaching.backend.domain.material.service;

import java.util.ArrayList;
import java.util.List;

// detailAnalysis 원문을 임베딩/Qdrant 색인에 적합한 크기의 청크로 분할하는 유틸리티
public class TextChunker {

    private static final int CHUNK_SIZE = 900;
    private static final int CHUNK_OVERLAP = 150;

    private TextChunker() {
    }

    public static List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        String trimmed = text.strip();
        int length = trimmed.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);
            chunks.add(trimmed.substring(start, end));
            if (end == length) {
                break;
            }
            start = end - CHUNK_OVERLAP;
        }
        return chunks;
    }
}
