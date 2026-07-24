package com.teaching.backend.domain.material.dto;

public record MaterialRestoreResponse(
        Long materialId,
        Boolean isDeleted
) {

    public static MaterialRestoreResponse of(
            Long materialId,
            Boolean isDeleted
    ) {
        return new MaterialRestoreResponse(materialId, isDeleted);
    }
}
