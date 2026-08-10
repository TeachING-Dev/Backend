package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.enums.MembershipType;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FolderMaterialCapacityValidator {

    static final int MAX_ACTIVE_MATERIALS_PER_FOLDER = 15;

    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;

    public void validateCanAdd(Long userId, Long folderId, long additionalCount) {
        if (folderId == null || additionalCount <= 0) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        if (user.getMembershipType() == MembershipType.PREMIUM) {
            return;
        }

        long activeMaterialCount = materialRepository.countByFolder_Id(folderId);
        if (activeMaterialCount + additionalCount > MAX_ACTIVE_MATERIALS_PER_FOLDER) {
            throw new MaterialException(MaterialErrorCode.FOLDER_MATERIAL_LIMIT_EXCEEDED);
        }
    }
}
