package com.teaching.backend.domain.trash.service;

import com.teaching.backend.domain.folder.dto.request.FolderIdsRequest;
import com.teaching.backend.domain.folder.dto.response.FolderTrashRestoreResponse;
import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.dto.MaterialRestoreResponse;
import com.teaching.backend.domain.material.dto.request.MaterialIdsRequest;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.dto.response.MaterialTagResponse;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.teachingmap.dto.request.TeachingMapIdsRequest;
import com.teaching.backend.domain.teachingmap.dto.response.SourcePlatform;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapRestoreResponse;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapErrorCode;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapException;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapPlatformProjection;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapStepRepository;
import com.teaching.backend.domain.trash.dto.response.TrashFolderItemResponse;
import com.teaching.backend.domain.trash.dto.response.TrashFolderListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashFolderMaterialListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashMaterialItemResponse;
import com.teaching.backend.domain.trash.dto.response.TrashMaterialListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashTeachingMapItemResponse;
import com.teaching.backend.domain.trash.dto.response.TrashTeachingMapListResponse;
import com.teaching.backend.domain.trash.exception.TrashErrorCode;
import com.teaching.backend.domain.trash.exception.TrashException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialTagRepository materialTagRepository;
    private final TeachingMapRepository teachingMapRepository;
    private final TeachingMapStepRepository teachingMapStepRepository;

    @Value("${app.icon-base-url}")
    private String iconBaseUrl;

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

        return TrashMaterialListResponse.of(materials.map(toTrashMaterialItem(materials.getContent())));
    }

    /**
     * 휴지통 폴더 상세 조회. 폴더 삭제 시 함께 휴지통으로 이동했던 내부 자료 목록을 페이지네이션하여 보여준다.
     */
    public TrashFolderMaterialListResponse getTrashedFolderMaterials(Long userId, Long folderId, String sort, Integer page) {
        Folder folder = getTrashedFolder(userId, folderId);

        PageRequest pageRequest = PageRequest.of(resolvePage(page), MATERIAL_PAGE_SIZE);
        Page<Material> materials = isOldest(sort)
                ? materialRepository.findTrashedByFolderIdOrderByDeletedAtAsc(folderId, userId, pageRequest)
                : materialRepository.findTrashedByFolderIdOrderByDeletedAtDesc(folderId, userId, pageRequest);

        return TrashFolderMaterialListResponse.of(folder, materials.map(toTrashMaterialItem(materials.getContent())));
    }

    private Folder getTrashedFolder(Long userId, Long folderId) {
        if (folderRepository.countByIdIncludingDeleted(folderId) == 0) {
            throw new FolderException(FolderErrorCode.FOLDER_NOT_FOUND);
        }
        if (folderRepository.countByIdAndUserIdIncludingDeleted(folderId, userId) == 0) {
            throw new FolderException(FolderErrorCode.FOLDER_ACCESS_DENIED);
        }
        return folderRepository.findTrashedByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new FolderException(FolderErrorCode.FOLDER_NOT_FOUND));
    }

    private Function<Material, TrashMaterialItemResponse> toTrashMaterialItem(List<Material> materials) {
        List<Long> materialIds = materials.stream().map(Material::getId).toList();
        Map<Long, String> summaryByMaterialId = materialIds.isEmpty()
                ? Map.of()
                : materialAnalysisRepository.findAllActiveByMaterialIds(materialIds).stream()
                .collect(Collectors.toMap(
                        ma -> ma.getMaterial().getId(),
                        MaterialAnalysis::getSummary
                ));
        Map<Long, List<MaterialTagResponse>> tagsByMaterialId = materialIds.isEmpty()
                ? Map.of()
                : materialTagRepository.findAllWithTagByMaterialIds(materialIds).stream()
                .collect(Collectors.groupingBy(
                        mt -> mt.getMaterial().getId(),
                        Collectors.mapping(MaterialTagResponse::from, Collectors.toList())
                ));

        return m -> TrashMaterialItemResponse.from(
                m,
                summaryByMaterialId.get(m.getId()),
                tagsByMaterialId.getOrDefault(m.getId(), List.of())
        );
    }

    public TrashTeachingMapListResponse getTrashedTeachingMaps(Long userId, String sort, Integer page) {
        PageRequest pageRequest = PageRequest.of(resolvePage(page), TEACHING_MAP_PAGE_SIZE);
        Page<TeachingMap> teachingMaps = isOldest(sort)
                ? teachingMapRepository.findTrashedByUserIdOrderByDeletedAtAsc(userId, pageRequest)
                : teachingMapRepository.findTrashedByUserIdOrderByDeletedAtDesc(userId, pageRequest);

        List<Long> teachingMapIds = teachingMaps.map(TeachingMap::getId).toList();
        // findActivePlatformTypesByTeachingMapIdIn 은 스텝 단위 조회라 같은 플랫폼이 여러 번 나올 수 있어,
        // LinkedHashSet 으로 순서를 유지하면서 중복을 제거한다.
        Map<Long, List<PlatformType>> platformTypesByTeachingMapId = teachingMapIds.isEmpty()
                ? Map.of()
                : teachingMapStepRepository.findActivePlatformTypesByTeachingMapIdIn(teachingMapIds, userId).stream()
                .collect(Collectors.groupingBy(
                        TeachingMapPlatformProjection::getTeachingMapId,
                        Collectors.mapping(
                                TeachingMapPlatformProjection::getPlatformType,
                                Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new)
                        )
                ));

        return TrashTeachingMapListResponse.of(teachingMaps.map(tm ->
                TrashTeachingMapItemResponse.from(
                        tm,
                        platformTypesByTeachingMapId.getOrDefault(tm.getId(), List.of()).stream()
                                .map(this::toSourcePlatform)
                                .toList()
                )
        ));
    }

    private SourcePlatform toSourcePlatform(PlatformType platformType) {
        return new SourcePlatform(platformType.name(), iconBaseUrl + "/" + platformType.getIconPath());
    }

    /**
     * 다중 선택 복구. 요청받은 ID 중 실제로 복구 가능한(휴지통에 있고, 자료는 상위 폴더도 활성 상태인)
     * ID만 골라 한 번의 UPDATE로 복구하고, 나머지는 실패 목록으로 돌려준다.
     */
    @Transactional
    public MaterialRestoreResponse restoreMaterials(Long userId, MaterialIdsRequest request) {
        List<Long> requestedIds = requireMaterialIds(request);

        List<Long> restorableIds = materialRepository.findRestorableTrashedIds(requestedIds, userId);
        List<MaterialRestoreResponse.RestoredMaterial> restored = List.of();
        if (!restorableIds.isEmpty()) {
            materialRepository.restoreTrashedMaterials(restorableIds, userId);
            // 자료는 원래 소속 폴더로 그대로 복구되므로(folder_id 변경 없음), 복구 직후 그 폴더명을 조회해
            // 프론트가 "OO 폴더로 복구되었습니다" 토스트를 표시할 수 있게 한다.
            restored = materialRepository.findFolderNamesByIds(restorableIds, userId).stream()
                    .map(p -> new MaterialRestoreResponse.RestoredMaterial(p.getMaterialId(), p.getFolderName()))
                    .toList();
        }

        return MaterialRestoreResponse.of(restored, failedIds(requestedIds, restorableIds));
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

    /**
     * 폴더 다중 선택 복구. 폴더마다 "휴지통에 있고 활성 폴더와 이름이 겹치지 않는지" 확인과
     * 복구 UPDATE를 하나의 원자적 쿼리로 묶어 처리하므로, 실제로 갱신된 폴더만 restoredIds에 담긴다.
     */
    @Transactional
    public FolderTrashRestoreResponse restoreFolders(Long userId, FolderIdsRequest request) {
        List<Long> requestedIds = requireFolderIds(request);

        List<Long> restoredIds = new ArrayList<>();
        for (Long folderId : requestedIds) {
            if (folderRepository.restoreTrashedFolderIfNameAvailable(folderId, userId) > 0) {
                restoredIds.add(folderId);
                materialRepository.restoreTrashedMaterialsByFolder(folderId, userId);
            }
        }

        return FolderTrashRestoreResponse.of(restoredIds, failedIds(requestedIds, restoredIds));
    }

    private List<Long> requireFolderIds(FolderIdsRequest request) {
        if (request == null || request.folderIds() == null || request.folderIds().isEmpty()) {
            throw new FolderException(FolderErrorCode.FOLDER_IDS_REQUIRED);
        }
        return request.folderIds();
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
        List<Long> failed = new ArrayList<>(new LinkedHashSet<>(requestedIds));
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
