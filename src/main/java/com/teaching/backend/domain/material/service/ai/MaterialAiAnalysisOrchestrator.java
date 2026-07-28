package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.folder.service.FolderService;
import com.teaching.backend.domain.material.dto.ai.MaterialAiHighlightResult;
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
        User user = userRepository.findById(preparationResult.userId())
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        Material material = materialRepository.save(Material.create(
                user,
                null,
                resolveTitle(preparationResult),
                preparationResult.originalUrl(),
                preparationResult.platformType()
        ));
        material.markAnalysisInProgress();

        Optional<MaterialAiAnalysisResult> previousResult = Optional.empty();
        MaterialAnalysis savedAnalysis = null;
        List<MaterialAiHighlightResult> highlights = List.of();
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
            highlights = stageResult.highlights();
            recommendedFolderName = stageResult.recommendedFolderName();
        }

        int chunkCount = materialIndexingService.indexMaterialContent(
                material,
                preparationResult.extractedContent().content()
        );
        if (previousResult.isPresent()) {
            materialAiAnalysisPersistenceService.saveHighlights(
                    savedAnalysis,
                    highlights
            );
        }
        material.markAnalysisCompleted();
        Folder recommendedFolder = resolveRecommendedFolder(preparationResult.userId(), recommendedFolderName);

        List<String> tags = previousResult
                .map(MaterialAiAnalysisResult::tags)
                .orElseGet(List::of);

        return new MaterialAiAnalysisPipelineResult(
                material.getId(),
                preparationResult.userId(),
                preparationResult.originalUrl(),
                preparationResult.platformType(),
                preparationResult.extractedContent(),
                savedAnalysis == null ? null : savedAnalysis.getId(),
                chunkCount,
                highlights,
                recommendedFolder == null ? null : recommendedFolder.getId(),
                recommendedFolder == null ? null : recommendedFolder.getName(),
                tags
        );
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
