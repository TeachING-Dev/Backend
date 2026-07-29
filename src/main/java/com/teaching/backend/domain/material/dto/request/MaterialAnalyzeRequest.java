package com.teaching.backend.domain.material.dto.request;

public record MaterialAnalyzeRequest(
        String url,
        Boolean forceAnalyze
) {
    public boolean isForceAnalyze() {
        return Boolean.TRUE.equals(forceAnalyze);
    }
}