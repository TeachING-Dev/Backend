package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.material.entity.MaterialAnalysis;

import java.time.LocalDateTime;

public record MaterialAnalysisResponse(
        Long materialAnalysisId,
        Long materialId,
        String shortSummary,
        String fullAnalysis,
        boolean isUserEdited,
        LocalDateTime generatedAt,
        LocalDateTime updatedAt
) {

    public static MaterialAnalysisResponse from(MaterialAnalysis analysis) {
        return new MaterialAnalysisResponse(
                analysis.getId(),
                analysis.getMaterial().getId(),
                analysis.getSummary(),
                analysis.getDetailAnalysis(),
                analysis.isUserEdited(),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt()
        );
    }
}
