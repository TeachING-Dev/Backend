package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.dto.request.MaterialAnalysisSummaryUpdateRequest;
import com.teaching.backend.domain.material.dto.request.MaterialIdsRequest;
import com.teaching.backend.domain.material.dto.request.MaterialMoveRequest;
import com.teaching.backend.domain.material.dto.response.MaterialAnalysisResponse;
import com.teaching.backend.domain.material.dto.response.MaterialAnalysisSummaryUpdateResponse;
import com.teaching.backend.domain.material.dto.response.MaterialDetailResponse;
import com.teaching.backend.domain.material.dto.response.MaterialMoveResponse;
import com.teaching.backend.domain.material.dto.response.MaterialOriginUrlResponse;
import com.teaching.backend.domain.material.dto.response.MaterialRestoreResponse;
import com.teaching.backend.domain.material.dto.response.MaterialTagResponse;
import com.teaching.backend.domain.material.dto.response.MaterialTrashResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialTagRepository materialTagRepository;
    private final FolderRepository folderRepository;

    public MaterialDetailResponse getMaterialDetail(
            Long userId,
            Long folderId,
            Long materialId
    ) {
        Material material = getOwnedMaterial(userId, folderId, materialId);
        String summary = materialAnalysisRepository.findByMaterialId(materialId)
                .map(MaterialAnalysis::getSummary)
                .orElse(null);

        return MaterialDetailResponse.of(material, summary, getTagNames(materialId));
    }

    public MaterialAnalysisResponse getMaterialAnalysis(
            Long userId,
            Long folderId,
            Long materialId
    ) {
        getOwnedMaterial(userId, folderId, materialId);

        return MaterialAnalysisResponse.from(getOwnedAnalysis(materialId));
    }

    @Transactional
    public MaterialAnalysisSummaryUpdateResponse updateAnalysisSummary(
            Long userId,
            Long folderId,
            Long materialId,
            MaterialAnalysisSummaryUpdateRequest request
    ) {
        getOwnedMaterial(userId, folderId, materialId);
        String shortSummary = validateAndNormalizeSummary(request);

        MaterialAnalysis analysis = getOwnedAnalysis(materialId);
        analysis.editSummary(shortSummary);

        return MaterialAnalysisSummaryUpdateResponse.of(materialId, analysis);
    }

    public List<MaterialTagResponse> getMaterialTags(
            Long userId,
            Long folderId,
            Long materialId
    ) {
        getOwnedMaterial(userId, folderId, materialId);

        return materialTagRepository.findAllWithTagByMaterialIds(List.of(materialId))
                .stream()
                .map(MaterialTagResponse::from)
                .toList();
    }

    public MaterialOriginUrlResponse getMaterialOriginUrl(
            Long userId,
            Long folderId,
            Long materialId
    ) {
        Material material = getOwnedMaterial(userId, folderId, materialId);

        return MaterialOriginUrlResponse.of(material.getId(), material.getOriginalUrl());
    }

    @Transactional
    public MaterialMoveResponse moveMaterials(
            Long userId,
            Long folderId,
            MaterialMoveRequest request
    ) {
        getOwnedFolder(userId, folderId);
        List<Long> materialIds = validateMaterialIds(request == null ? null : request.materialIds());
        Long targetFolderId = validateTargetFolderId(request);
        Folder targetFolder = getOwnedFolder(userId, targetFolderId);

        List<Material> materials = findOwnedMaterialsInFolder(userId, folderId, materialIds);
        materials.forEach(material -> material.changeFolder(targetFolder));

        return MaterialMoveResponse.of(materials.size(), folderId, targetFolderId);
    }

    @Transactional
    public MaterialTrashResponse trashMaterials(
            Long userId,
            Long folderId,
            MaterialIdsRequest request
    ) {
        getOwnedFolder(userId, folderId);
        List<Long> materialIds = validateMaterialIds(request == null ? null : request.materialIds());

        List<Material> materials = findOwnedMaterialsInFolder(userId, folderId, materialIds);
        materials.forEach(Material::delete);

        return MaterialTrashResponse.of(materials.size(), folderId);
    }

    @Transactional
    public MaterialRestoreResponse restoreMaterials(
            Long userId,
            Long folderId,
            MaterialIdsRequest request
    ) {
        getOwnedFolder(userId, folderId);
        List<Long> materialIds = validateMaterialIds(request == null ? null : request.materialIds());

        long deletedCount = materialRepository.countDeletedByIdsAndUserId(materialIds, userId);
        if (deletedCount != materialIds.size()) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_NOT_FOUND);
        }

        int restoredCount = materialRepository.restoreDeletedMaterials(materialIds, folderId, userId);

        return MaterialRestoreResponse.of(restoredCount, folderId);
    }

    private List<Material> findOwnedMaterialsInFolder(
            Long userId,
            Long folderId,
            List<Long> materialIds
    ) {
        List<Material> materials = materialRepository.findAllByIdInAndFolder_IdAndUser_Id(
                materialIds,
                folderId,
                userId
        );

        if (materials.size() != materialIds.size()) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_NOT_FOUND);
        }

        return materials;
    }

    private List<String> getTagNames(Long materialId) {
        return materialTagRepository.findAllWithTagByMaterialIds(List.of(materialId))
                .stream()
                .map(materialTag -> materialTag.getTag().getName())
                .toList();
    }

    private MaterialAnalysis getOwnedAnalysis(Long materialId) {
        return materialAnalysisRepository.findByMaterialId(materialId)
                .orElseThrow(() -> new MaterialException(MaterialErrorCode.MATERIAL_ANALYSIS_NOT_FOUND));
    }

    private Material getOwnedMaterial(
            Long userId,
            Long folderId,
            Long materialId
    ) {
        validateMaterialId(materialId);
        getOwnedFolder(userId, folderId);

        return materialRepository.findByIdAndFolder_IdAndUser_Id(materialId, folderId, userId)
                .orElseThrow(() -> resolveMaterialLookupException(materialId));
    }

    private RuntimeException resolveMaterialLookupException(Long materialId) {
        if (materialRepository.existsById(materialId)) {
            return new MaterialException(MaterialErrorCode.MATERIAL_ACCESS_DENIED);
        }

        return new MaterialException(MaterialErrorCode.MATERIAL_NOT_FOUND);
    }

    private Folder getOwnedFolder(
            Long userId,
            Long folderId
    ) {
        validateFolderId(folderId);

        return folderRepository.findByIdAndUser_Id(folderId, userId)
                .orElseThrow(() -> resolveFolderLookupException(folderId));
    }

    private RuntimeException resolveFolderLookupException(Long folderId) {
        if (folderRepository.existsById(folderId)) {
            return new FolderException(FolderErrorCode.FOLDER_ACCESS_DENIED);
        }

        return new FolderException(FolderErrorCode.FOLDER_NOT_FOUND);
    }

    private void validateFolderId(Long folderId) {
        if (folderId == null || folderId <= 0) {
            throw new FolderException(FolderErrorCode.INVALID_FOLDER_ID);
        }
    }

    private void validateMaterialId(Long materialId) {
        if (materialId == null || materialId <= 0) {
            throw new MaterialException(MaterialErrorCode.INVALID_MATERIAL_ID);
        }
    }

    private List<Long> validateMaterialIds(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_IDS_REQUIRED);
        }

        return materialIds.stream().distinct().toList();
    }

    private Long validateTargetFolderId(MaterialMoveRequest request) {
        Long targetFolderId = request == null ? null : request.targetFolderId();
        if (targetFolderId == null || targetFolderId <= 0) {
            throw new MaterialException(MaterialErrorCode.TARGET_FOLDER_ID_REQUIRED);
        }

        return targetFolderId;
    }

    private String validateAndNormalizeSummary(MaterialAnalysisSummaryUpdateRequest request) {
        if (request == null || request.shortSummary() == null || request.shortSummary().isBlank()) {
            throw new MaterialException(MaterialErrorCode.SUMMARY_REQUIRED);
        }

        return request.shortSummary().trim();
    }
}
