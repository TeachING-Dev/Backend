package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.material.entity.Material;

import java.time.LocalDateTime;

public record TrashMaterialItemResponse(
        Long materialId,
        String title,
        LocalDateTime deletedAt
) {

    public static TrashMaterialItemResponse from(Material material) {
        return new TrashMaterialItemResponse(
                material.getId(),
                material.getTitle(),
                material.getDeletedAt()
        );
    }
}
