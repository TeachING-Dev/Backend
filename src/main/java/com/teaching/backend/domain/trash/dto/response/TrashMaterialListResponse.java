package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.material.entity.Material;

import java.time.LocalDateTime;

public record TrashMaterialListResponse(
        Long materialId,
        String analysisTitle,
        LocalDateTime deletedAt
) {

    public static TrashMaterialListResponse from(Material material) {
        return new TrashMaterialListResponse(
                material.getId(),
                material.getAnalysisTitle(),
                material.getDeletedAt()
        );
    }
}
