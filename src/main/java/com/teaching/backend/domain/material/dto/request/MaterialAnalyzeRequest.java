package com.teaching.backend.domain.material.dto.request;

public record MaterialAnalyzeRequest(
        String url,
        Long folderId,
        Boolean forceAnalyze
) {

    public boolean isForceAnalyze() {
        return Boolean.TRUE.equals(forceAnalyze);
    }
}
