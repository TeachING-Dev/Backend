package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.material.entity.MaterialAnalysis;

import java.time.LocalDateTime;

public record MaterialAnalysisSummaryUpdateResponse(
        Long materialId,
        String shortSummary,
        boolean isUserEdited,
        LocalDateTime updatedAt
) {

    public static MaterialAnalysisSummaryUpdateResponse of(Long materialId, MaterialAnalysis analysis) {
        return new MaterialAnalysisSummaryUpdateResponse(
                materialId,
                analysis.getSummary(),
                analysis.isUserEdited(),
                analysis.getUpdatedAt()
        );
    }
}
