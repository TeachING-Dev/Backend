package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.trash.util.RelativeTimeFormatter;

public record TrashMaterialListResponse(
        Long materialId,
        String analysisTitle,
        String deletedAt
) {

    public static TrashMaterialListResponse from(Material material) {
        return new TrashMaterialListResponse(
                material.getId(),
                material.getAnalysisTitle(),
                RelativeTimeFormatter.format(material.getDeletedAt())
        );
    }
}
