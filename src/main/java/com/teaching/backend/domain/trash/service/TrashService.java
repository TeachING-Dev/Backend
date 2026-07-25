package com.teaching.backend.domain.trash.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.dto.MaterialRestoreResponse;
import com.teaching.backend.domain.material.dto.request.MaterialIdsRequest;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.teachingmap.dto.request.TeachingMapIdsRequest;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapRestoreResponse;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapErrorCode;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapException;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.domain.trash.dto.response.TrashFolderItemResponse;
import com.teaching.backend.domain.trash.dto.response.TrashFolderListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashMaterialItemResponse;
import com.teaching.backend.domain.trash.dto.response.TrashMaterialListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashTeachingMapItemResponse;
import com.teaching.backend.domain.trash.dto.response.TrashTeachingMapListResponse;
import com.teaching.backend.domain.trash.exception.TrashErrorCode;
import com.teaching.backend.domain.trash.exception.TrashException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    private static final String LATEST = "latest";
    private static final String OLDEST = "oldest";

    private static final int FOLDER_PAGE_SIZE = 9;
    private static final int MATERIAL_PAGE_SIZE = 10;
    private static final int TEACHING_MAP_PAGE_SIZE = 10;

    private final FolderRepository folderRepository;
    private final MaterialRepository materialRepository;
    private final TeachingMapRepository teachingMapRepository;

    public TrashFolderListResponse getTrashedFolders(Long userId, String sort, Integer page) {
        PageRequest pageRequest = PageRequest.of(resolvePage(page), FOLDER_PAGE_SIZE);
        Page<Folder> folders = isOldest(sort)
                ? folderRepository.findTrashedByUserIdOrderByDeletedAtAsc(userId, pageRequest)
                : folderRepository.findTrashedByUserIdOrderByDeletedAtDesc(userId, pageRequest);

        return TrashFolderListResponse.of(folders.map(TrashFolderItemResponse::from));
    }

    public TrashMaterialListResponse getTrashedMaterials(Long userId, String sort, Integer page) {
        PageRequest pageRequest = PageRequest.of(resolvePage(page), MATERIAL_PAGE_SIZE);
        Page<Material> materials = isOldest(sort)
                ? materialRepository.findTrashedByUserIdOrderByDeletedAtAsc(userId, pageRequest)
                : materialRepository.findTrashedByUserIdOrderByDeletedAtDesc(userId, pageRequest);

        return TrashMaterialListResponse.of(materials.map(TrashMaterialItemResponse::from));
    }

    public TrashTeachingMapListResponse getTrashedTeachingMaps(Long userId, String sort, Integer page) {
        PageRequest pageRequest = PageRequest.of(resolvePage(page), TEACHING_MAP_PAGE_SIZE);
        Page<TeachingMap> teachingMaps = isOldest(sort)
                ? teachingMapRepository.findTrashedByUserIdOrderByDeletedAtAsc(userId, pageRequest)
                : teachingMapRepository.findTrashedByUserIdOrderByDeletedAtDesc(userId, pageRequest);

        return TrashTeachingMapListResponse.of(teachingMaps.map(TrashTeachingMapItemResponse::from));
    }

    /**
     * 다중 선택 복구. 요청받은 ID 중 실제로 복구 가능한(휴지통에 있고, 자료는 상위 폴더도 활성 상태인)
     * ID만 골라 한 번의 UPDATE로 복구하고, 나머지는 실패 목록으로 돌려준다.
     */
    @Transactional
    public MaterialRestoreResponse restoreMaterials(Long userId, MaterialIdsRequest request) {
        List<Long> requestedIds = requireMaterialIds(request);

        List<Long> restorableIds = materialRepository.findRestorableTrashedIds(requestedIds, userId);
        if (!restorableIds.isEmpty()) {
            materialRepository.restoreTrashedMaterials(restorableIds, userId);
        }

        return MaterialRestoreResponse.of(restorableIds, failedIds(requestedIds, restorableIds));
    }

    @Transactional
    public TeachingMapRestoreResponse restoreTeachingMaps(Long userId, TeachingMapIdsRequest request) {
        List<Long> requestedIds = requireTeachingMapIds(request);

        List<Long> restorableIds = teachingMapRepository.findRestorableTrashedIds(requestedIds, userId);
        if (!restorableIds.isEmpty()) {
            teachingMapRepository.restoreTrashedTeachingMaps(restorableIds, userId);
        }

        return TeachingMapRestoreResponse.of(restorableIds, failedIds(requestedIds, restorableIds));
    }

    private List<Long> requireMaterialIds(MaterialIdsRequest request) {
        if (request == null || request.materialIds() == null || request.materialIds().isEmpty()) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_IDS_REQUIRED);
        }
        return request.materialIds();
    }

    private List<Long> requireTeachingMapIds(TeachingMapIdsRequest request) {
        if (request == null || request.teachingMapIds() == null || request.teachingMapIds().isEmpty()) {
            throw new TeachingMapException(TeachingMapErrorCode.TEACHING_MAP_IDS_REQUIRED);
        }
        return request.teachingMapIds();
    }

    private List<Long> failedIds(List<Long> requestedIds, List<Long> restoredIds) {
        List<Long> failed = new ArrayList<>(requestedIds);
        failed.removeAll(restoredIds);
        return failed;
    }

    private int resolvePage(Integer page) {
        return (page == null || page < 0) ? 0 : page;
    }

    private boolean isOldest(String sort) {
        if (sort == null || sort.isBlank()) {
            return false;
        }

        String normalized = sort.trim().toLowerCase();
        if (LATEST.equals(normalized)) {
            return false;
        }
        if (OLDEST.equals(normalized)) {
            return true;
        }

        throw new TrashException(TrashErrorCode.INVALID_SORT);
    }
}
