package com.teaching.backend.domain.material.dto;

import java.util.List;

public record MaterialRestoreResponse(
        List<RestoredMaterial> restored,
        List<Long> failedIds
) {

    /** 복구된 자료가 어느 폴더로 돌아갔는지(원래 소속 폴더) 프론트 토스트 표시용으로 함께 내려준다. */
    public record RestoredMaterial(
            Long materialId,
            String folderName
    ) {
    }

    public static MaterialRestoreResponse of(
            List<RestoredMaterial> restored,
            List<Long> failedIds
    ) {
        return new MaterialRestoreResponse(restored, failedIds);
    }
}
