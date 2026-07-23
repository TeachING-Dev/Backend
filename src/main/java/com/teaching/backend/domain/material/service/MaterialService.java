package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.dto.MaterialListResponse;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.request.MaterialAnalysisGenerateRequest;
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
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.entity.Tag;
import com.teaching.backend.domain.tag.exception.TagErrorCode;
import com.teaching.backend.domain.tag.exception.TagException;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.tag.repository.TagRepository;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialTagRepository materialTagRepository;
    private final TagRepository tagRepository;
    private final FolderRepository folderRepository;
    private final OpenAiClient openAiClient;
    private final MaterialAnalysisPromptBuilder materialAnalysisPromptBuilder;
    private final MaterialAiAnalysisResponseParser materialAiAnalysisResponseParser;
    private final MaterialPlatformResolver materialPlatformResolver;

    public List<MaterialListResponse> getMaterialList(Long userId, Integer size) {
        List<Material> materials = findRecentMaterials(userId, size);
        if (materials.isEmpty()) {
            return List.of();
        }

        List<Long> materialIds = materials.stream()
                .map(Material::getId)
                .toList();

        Map<Long, String> summaryByMaterialId = materialAnalysisRepository.findAllActiveByMaterialIds(materialIds)
                .stream()
                .collect(Collectors.toMap(
                        analysis -> analysis.getMaterial().getId(),
                        MaterialAnalysis::getSummary,
                        (current, ignored) -> current
                ));

        return materials.stream()
                .map(material -> MaterialListResponse.of(
                        material,
                        summaryByMaterialId.get(material.getId())
                ))
                .toList();
    }

    @Transactional
    public void deleteMaterialTag(Long userId, Long materialTagId) {
        validateMaterialTagId(materialTagId);

        MaterialTag materialTag = materialTagRepository.findByIdWithMaterialAndUser(materialTagId)
                .orElseThrow(() -> new TagException(TagErrorCode.TAG_NOT_FOUND));

        if (!materialTag.getMaterial().getUser().getId().equals(userId)) {
            throw new TagException(TagErrorCode.TAG_ACCESS_DENIED);
        }

        materialTagRepository.delete(materialTag);
    }

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

    @Transactional(noRollbackFor = MaterialException.class)
    public MaterialAnalysisResponse generateMaterialWithAnalysis(
            Long userId,
            Long folderId,
            MaterialAnalysisGenerateRequest request
    ) {
        Folder folder = getOwnedFolder(userId, folderId);
        String title = validateAndNormalizeTitle(request);
        String originalUrl = validateAndNormalizeOriginalUrl(request);
        String content = validateAndNormalizeContent(request);
        PlatformType platformType = materialPlatformResolver.resolve(request.platformType(), originalUrl);

        Material material = materialRepository.save(
                Material.create(folder.getUser(), folder, title, originalUrl, platformType)
        );

        MaterialAiAnalysisResult aiResult;
        try {
            String systemPrompt = materialAnalysisPromptBuilder.buildSystemPrompt(userId);
            String userMessage = materialAnalysisPromptBuilder.buildUserMessage(originalUrl, content);
            String rawResponse = openAiClient.chatCompleteJson(systemPrompt, userMessage);
            aiResult = materialAiAnalysisResponseParser.parse(rawResponse);
        } catch (MaterialException e) {
            material.failAnalysis();
            throw e;
        } catch (RuntimeException e) {
            material.failAnalysis();
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
        }

        MaterialAnalysis analysis = materialAnalysisRepository.save(
                MaterialAnalysis.create(material, aiResult.shortSummary(), aiResult.longAnalysis(), "v1")
        );
        saveTags(material, aiResult.tags());
        material.markAnalysisCompleted();

        return MaterialAnalysisResponse.from(analysis);
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

        int restoredCount = materialRepository.restoreDeletedMaterials(materialIds, folderId, userId);
        if (restoredCount != materialIds.size()) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_NOT_FOUND);
        }

        return MaterialRestoreResponse.of(restoredCount, folderId);
    }

    private List<Material> findRecentMaterials(Long userId, Integer size) {
        Sort recentSort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );

        if (size == null) {
            return materialRepository.findAllByUser_Id(userId, recentSort);
        }

        if (size <= 0) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        return materialRepository.findAllByUser_Id(
                userId,
                PageRequest.of(0, size, recentSort)
        ).getContent();
    }

    private void validateMaterialTagId(Long materialTagId) {
        if (materialTagId == null || materialTagId <= 0) {
            throw new TagException(TagErrorCode.TAG_NOT_FOUND);
        }
    }

    private void saveTags(Material material, List<String> tagNames) {
        tagNames.stream()
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .forEach(name -> {
                    Tag tag = tagRepository.findByName(name)
                            .orElseGet(() -> tagRepository.save(Tag.create(name)));
                    materialTagRepository.save(MaterialTag.create(material, tag));
                });
    }

    private String validateAndNormalizeTitle(MaterialAnalysisGenerateRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new MaterialException(MaterialErrorCode.TITLE_REQUIRED);
        }

        return request.title().trim();
    }

    private String validateAndNormalizeOriginalUrl(MaterialAnalysisGenerateRequest request) {
        if (request == null || request.originalUrl() == null || request.originalUrl().isBlank()) {
            throw new MaterialException(MaterialErrorCode.ORIGINAL_URL_REQUIRED);
        }

        return request.originalUrl().trim();
    }

    private String validateAndNormalizeContent(MaterialAnalysisGenerateRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new MaterialException(MaterialErrorCode.CONTENT_REQUIRED);
        }

        return request.content().trim();
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
