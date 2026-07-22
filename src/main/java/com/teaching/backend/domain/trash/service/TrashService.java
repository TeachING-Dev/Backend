package com.teaching.backend.domain.trash.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.dto.MaterialRestoreResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapRestoreResponse;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapErrorCode;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapException;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.domain.trash.dto.response.TrashFolderListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashMaterialListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashTeachingMapListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 휴지통 도메인 서비스.
 *
 * Folder/Material/TeachingMap 은 전부 {@code @SQLRestriction("deleted_at IS NULL")}로
 * 소프트딜리트된 행을 JPQL에서 자동 제외하므로, 휴지통(삭제된 행) 조회/복구는
 * FolderRepository 의 기존 패턴을 그대로 따라 native query 로 우회한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrashService {

    private static final String OLDEST = "oldest";

    private final FolderRepository folderRepository;
    private final MaterialRepository materialRepository;
    private final TeachingMapRepository teachingMapRepository;

    public List<TrashFolderListResponse> getTrashedFolders(Long userId, String sort) {
        List<Folder> folders = isOldest(sort)
                ? folderRepository.findTrashedByUserIdOrderByDeletedAtAsc(userId)
                : folderRepository.findTrashedByUserIdOrderByDeletedAtDesc(userId);

        return folders.stream().map(TrashFolderListResponse::from).toList();
    }

    public List<TrashMaterialListResponse> getTrashedMaterials(Long userId, String sort) {
        List<Material> materials = isOldest(sort)
                ? materialRepository.findTrashedByUserIdOrderByDeletedAtAsc(userId)
                : materialRepository.findTrashedByUserIdOrderByDeletedAtDesc(userId);

        return materials.stream().map(TrashMaterialListResponse::from).toList();
    }

    public List<TrashTeachingMapListResponse> getTrashedTeachingMaps(Long userId, String sort) {
        List<TeachingMap> teachingMaps = isOldest(sort)
                ? teachingMapRepository.findTrashedByUserIdOrderByDeletedAtAsc(userId)
                : teachingMapRepository.findTrashedByUserIdOrderByDeletedAtDesc(userId);

        return teachingMaps.stream().map(TrashTeachingMapListResponse::from).toList();
    }

    @Transactional
    public MaterialRestoreResponse restoreMaterial(Long userId, Long materialId) {
        if (materialRepository.countByIdAndUserIdIncludingDeleted(materialId, userId) == 0) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_NOT_FOUND);
        }

        if (materialRepository.countDeletedByIdAndUserId(materialId, userId) == 0) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_NOT_IN_TRASH);
        }

        if (materialRepository.countWithTrashedParentFolder(materialId, userId) > 0) {
            throw new FolderException(FolderErrorCode.PARENT_FOLDER_IN_TRASH);
        }

        int restoredCount = materialRepository.restoreDeletedMaterial(materialId, userId);
        if (restoredCount == 0) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_NOT_FOUND);
        }

        return MaterialRestoreResponse.of(materialId, false);
    }

    @Transactional
    public TeachingMapRestoreResponse restoreTeachingMap(Long userId, Long teachingMapId) {
        if (teachingMapRepository.countByIdAndUserIdIncludingDeleted(teachingMapId, userId) == 0) {
            throw new TeachingMapException(TeachingMapErrorCode.TEACHING_MAP_NOT_FOUND);
        }

        if (teachingMapRepository.countDeletedByIdAndUserId(teachingMapId, userId) == 0) {
            throw new TeachingMapException(TeachingMapErrorCode.TEACHING_MAP_NOT_IN_TRASH);
        }

        int restoredCount = teachingMapRepository.restoreDeletedTeachingMap(teachingMapId, userId);
        if (restoredCount == 0) {
            throw new TeachingMapException(TeachingMapErrorCode.TEACHING_MAP_NOT_FOUND);
        }

        return TeachingMapRestoreResponse.of(teachingMapId, false);
    }

    private boolean isOldest(String sort) {
        return sort != null && OLDEST.equalsIgnoreCase(sort.trim());
    }
}
