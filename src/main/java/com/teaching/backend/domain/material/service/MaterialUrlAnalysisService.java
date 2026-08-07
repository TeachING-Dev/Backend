package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.service.FolderService;
import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.dto.extract.MaterialAnalysisPreparationResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisPipelineResult;
import com.teaching.backend.domain.material.dto.request.MaterialAnalyzeRequest;
import com.teaching.backend.domain.material.dto.response.MaterialAnalyzeResponse;
import com.teaching.backend.domain.material.dto.response.MaterialTagResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialChunkRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.material.service.ai.MaterialAiAnalysisOrchestrator;
import com.teaching.backend.domain.material.service.extract.MaterialContentExtractorRegistry;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialUrlAnalysisService {

    private final FolderService folderService;
    private final MaterialRepository materialRepository;
    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final MaterialUrlValidator materialUrlValidator;
    private final MaterialPlatformResolver materialPlatformResolver;
    private final MaterialContentExtractorRegistry materialContentExtractorRegistry;
    private final MaterialAiAnalysisOrchestrator materialAiAnalysisOrchestrator;
    private final MaterialUrlAnalysisConcurrencyGuard materialUrlAnalysisConcurrencyGuard;
    private final MaterialTagRepository materialTagRepository;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MaterialAnalyzeResponse analyze(
            Long userId,
            MaterialAnalyzeRequest request
    ) {
        String originalUrl = validateAndNormalizeUrl(request);
        PlatformType platformType = materialPlatformResolver.resolve(null, originalUrl);

        if (request.isForceAnalyze()) {
            Material existingMaterial = findLatestCompletedMaterial(userId, originalUrl)
                    .orElse(null);
            return analyzeNewMaterial(userId, originalUrl, platformType, existingMaterial);
        }

        return materialUrlAnalysisConcurrencyGuard.executeSerialized(
                userId,
                originalUrl,
                () -> findLatestCompletedMaterial(userId, originalUrl)
                        .map(this::alreadyAnalyzedResponse),
                () -> analyzeNewMaterial(userId, originalUrl, platformType, null)
        );
    }

    private MaterialAnalyzeResponse analyzeNewMaterial(
            Long userId,
            String originalUrl,
            PlatformType platformType,
            Material existingMaterial
    ) {
        MaterialAnalysisPreparationResult preparationResult = prepareAnalysis(
                userId,
                originalUrl,
                platformType
        );
        // existingMaterial이 있으면(forceAnalyze로 같은 URL 재분석) 새 자료를 만드는 대신 재사용한다 —
        // 그렇지 않으면 재분석할 때마다 자료/청크/Qdrant 벡터가 중복으로 계속 쌓인다.
        MaterialAiAnalysisPipelineResult analysisResult = materialAiAnalysisOrchestrator.analyze(
                preparationResult,
                existingMaterial
        );

        List<MaterialTagResponse> tags = materialTagRepository.findAllByMaterialId(analysisResult.materialId()).stream()
                .map(MaterialTagResponse::from)
                .toList();
        return MaterialAnalyzeResponse.completed(analysisResult, tags, existingMaterial);
    }

    private String validateAndNormalizeUrl(MaterialAnalyzeRequest request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new MaterialException(MaterialErrorCode.ORIGINAL_URL_REQUIRED);
        }

        String originalUrl = request.url().trim();
        if (!materialUrlValidator.isValidHttpUrl(originalUrl)) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        return originalUrl;
    }

    private Optional<Material> findLatestCompletedMaterial(
            Long userId,
            String originalUrl
    ) {
        List<Material> materials = materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(
                userId,
                originalUrl
        );

        return materials.stream()
                .filter(material -> material.getAiStatus() == AiStatus.COMPLETED)
                .max(Comparator
                        .comparing(Material::getCreatedAt)
                .thenComparing(Material::getId));
    }

    private MaterialAnalyzeResponse alreadyAnalyzedResponse(Material material) {
        MaterialAnalysis materialAnalysis = materialAnalysisRepository.findByMaterialId(material.getId())
                .orElse(null);
        Long materialAnalysisId = materialAnalysis == null ? null : materialAnalysis.getId();

        int chunkCount = materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(material.getId()).size();

        List<MaterialTagResponse> tags = materialTagRepository.findAllByMaterialId(material.getId()).stream()
                .map(MaterialTagResponse::from)
                .toList();

        return MaterialAnalyzeResponse.alreadyAnalyzed(material,materialAnalysis, materialAnalysisId, chunkCount,tags);
    }

    MaterialAnalysisPreparationResult prepareAnalysis(
            Long userId,
            String originalUrl,
            PlatformType platformType
    ) {
        ExtractedMaterialContent extractedContent = materialContentExtractorRegistry.extract(platformType, originalUrl);
        return new MaterialAnalysisPreparationResult(
                userId,
                null,
                originalUrl,
                extractedContent.platformType(),
                extractedContent
        );
    }
}
