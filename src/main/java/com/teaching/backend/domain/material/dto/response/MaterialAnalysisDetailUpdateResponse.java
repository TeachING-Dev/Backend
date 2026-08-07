package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.material.entity.MaterialAnalysis;

import java.time.LocalDateTime;

public record MaterialAnalysisDetailUpdateResponse(
        Long materialId,
        String fullAnalysis,
        boolean isUserEdited,
        LocalDateTime updatedAt
) {

    public static MaterialAnalysisDetailUpdateResponse of(Long materialId, MaterialAnalysis analysis) {
        return new MaterialAnalysisDetailUpdateResponse(
                materialId,
                analysis.getDetailAnalysis(),
                analysis.isUserEdited(),
                analysis.getUpdatedAt()
        );
    }
}
