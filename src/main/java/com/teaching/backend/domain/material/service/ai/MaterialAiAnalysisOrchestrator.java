package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.folder.service.FolderService;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisPipelineResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiStageContext;
import com.teaching.backend.domain.material.dto.ai.MaterialAiStageResult;
import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.dto.extract.MaterialAnalysisPreparationResult;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.material.service.MaterialIndexingService;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MaterialAiAnalysisOrchestrator {

    private static final int MAX_TITLE_LENGTH = 200;

    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final FolderService folderService;
    private final MaterialRepository materialRepository;
    private final MaterialAiAnalysisStageRegistry materialAiAnalysisStageRegistry;
    private final MaterialAiAnalysisPersistenceService materialAiAnalysisPersistenceService;
    private final MaterialIndexingService materialIndexingService;

    @Transactional
    public MaterialAiAnalysisPipelineResult analyze(MaterialAnalysisPreparationResult preparationResult) {
        return analyze(preparationResult, null);
    }

    // existingMaterial이 주어지면(forceAnalyze로 같은 URL을 재분석하는 경우) 새 자료를 만들지 않고
    // 그 자료를 재사용한다 — 그래야 청크 재인덱싱(MaterialIndexingService)과 분석 결과 갱신
    // (MaterialAiAnalysisPersistenceService)이 같은 materialId를 보고 기존 청크/분석/벡터를
    // 덮어쓰지, 매번 새로 쌓지 않는다.
    @Transactional
    public MaterialAiAnalysisPipelineResult analyze(
            MaterialAnalysisPreparationResult preparationResult,
            Material existingMaterial
    ) {
        Material material = existingMaterial != null
                ? reuseMaterial(existingMaterial, preparationResult)
                : createMaterial(preparationResult);
        material.markAnalysisInProgress();

        Optional<MaterialAiAnalysisResult> previousResult = Optional.empty();
        MaterialAnalysis savedAnalysis = null;
        String recommendedFolderName = null;
        for (MaterialAiAnalysisStage stage : materialAiAnalysisStageRegistry.stagesInOrder()) {
            MaterialAiStageResult stageResult = stage.execute(new MaterialAiStageContext(
                    preparationResult.userId(),
                    preparationResult.folderId(),
                    preparationResult.originalUrl(),
                    preparationResult.platformType(),
                    preparationResult.extractedContent(),
                    material,
                    previousResult
            ));
            if (stageResult.analysisResult() != null) {
                savedAnalysis = materialAiAnalysisPersistenceService.saveAnalysisResult(
                        material,
                        stageResult.analysisResult()
                );
                previousResult = Optional.of(stageResult.analysisResult());
            }
            recommendedFolderName = stageResult.recommendedFolderName();
        }

        int chunkCount = materialIndexingService.indexMaterialContent(
                material,
                preparationResult.extractedContent().content()
        );
        material.markAnalysisCompleted();
        Folder recommendedFolder = resolveRecommendedFolder(preparationResult.userId(), recommendedFolderName);

        List<String> tags = previousResult
                .map(MaterialAiAnalysisResult::tags)
                .orElseGet(List::of);

        return new MaterialAiAnalysisPipelineResult(
                material.getId(),
                preparationResult.userId(),
                preparationResult.folderId(),
                savedAnalysis == null ? null : savedAnalysis.getSummary(),
                preparationResult.originalUrl(),
                preparationResult.platformType(),
                preparationResult.extractedContent(),
                savedAnalysis == null ? null : savedAnalysis.getId(),
                chunkCount,
                recommendedFolder == null ? null : recommendedFolder.getId(),
                recommendedFolder == null ? null : recommendedFolder.getName(),
                tags
        );
    }

    private Material createMaterial(MaterialAnalysisPreparationResult preparationResult) {
        User user = userRepository.findById(preparationResult.userId())
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        return materialRepository.save(Material.create(
                user,
                null,
                resolveTitle(preparationResult),
                preparationResult.originalUrl(),
                preparationResult.platformType()
        ));
    }

    // existingMaterial은 이미 이 유저 소유로 조회된 자료라 User를 다시 조회할 필요가 없다.
    private Material reuseMaterial(Material existingMaterial, MaterialAnalysisPreparationResult preparationResult) {
        existingMaterial.updateForReanalysis(resolveTitle(preparationResult), preparationResult.platformType());
        return existingMaterial;
    }

    private Folder resolveRecommendedFolder(Long userId, String recommendedFolderName) {
        if (recommendedFolderName == null || recommendedFolderName.isBlank()) {
            return null;
        }

        return folderRepository.findByUser_IdAndNameAndDeletedAtIsNull(userId, recommendedFolderName.trim())
                .orElse(null);
    }

    private String resolveTitle(MaterialAnalysisPreparationResult preparationResult) {
        ExtractedMaterialContent content = preparationResult.extractedContent();
        String title = content.title();
        if (title == null || title.isBlank()) {
            title = preparationResult.originalUrl();
        }

        title = title.trim();
        return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH);
    }
}
