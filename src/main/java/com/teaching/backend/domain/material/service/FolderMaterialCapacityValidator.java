package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FolderMaterialCapacityValidator {

    static final int MAX_ACTIVE_MATERIALS_PER_FOLDER = 15;

    private final MaterialRepository materialRepository;

    public void validateCanAdd(Long folderId, long additionalCount) {
        if (folderId == null || additionalCount <= 0) {
            return;
        }

        long activeMaterialCount = materialRepository.countByFolder_Id(folderId);
        if (activeMaterialCount + additionalCount > MAX_ACTIVE_MATERIALS_PER_FOLDER) {
            throw new MaterialException(MaterialErrorCode.FOLDER_MATERIAL_LIMIT_EXCEEDED);
        }
    }
}
