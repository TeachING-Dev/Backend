package com.teaching.backend.domain.teachingmap.dto.response;

import com.teaching.backend.domain.material.entity.MaterialAnalysis;

import java.util.List;

public record ExistingAiAnalysis(
        String summary,
        String detailAnalysis,
        List<HighlightResponse> highlights
) {
    public static ExistingAiAnalysis of(MaterialAnalysis analysis, List<HighlightResponse> highlights) {
        return new ExistingAiAnalysis(
                analysis.getSummary(),
                analysis.getDetailAnalysis(),
                highlights
        );
    }
}