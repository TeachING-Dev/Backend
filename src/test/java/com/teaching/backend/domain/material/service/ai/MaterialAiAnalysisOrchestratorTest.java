package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.folder.entity.Folder;
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
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.MaterialAiHighlightType;
import com.teaching.backend.domain.material.enums.MaterialAiStageType;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialAiAnalysisOrchestratorTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FolderService folderService;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialAiAnalysisStageRegistry stageRegistry;

    @Mock
    private MaterialAiAnalysisPersistenceService persistenceService;

    @InjectMocks
    private MaterialAiAnalysisOrchestrator orchestrator;

    @Test
    void analyzesPreparationResultAndSavesGeneralDbDataWithoutCompletedStatus() {
        User user = user();
        Folder folder = folder(user);
        MaterialAiAnalysisResult aiResult = new MaterialAiAnalysisResult("summary", "detail", List.of("tag"), null, null);
        MaterialAiHighlightResult highlight = new MaterialAiHighlightResult("important", MaterialAiHighlightType.CORE);
        MaterialAnalysis analysis = MaterialAnalysis.create(
                Material.create(user, folder, "Title", "https://example.com", PlatformType.BLOG),
                "summary",
                "detail",
                "v1"
        );
        ReflectionTestUtils.setField(analysis, "id", 200L);
        MaterialAiAnalysisStage stage = new MaterialAiAnalysisStage() {
            @Override
            public MaterialAiStageType type() {
                return MaterialAiStageType.CONTENT_ANALYSIS;
            }

            @Override
            public MaterialAiStageResult execute(MaterialAiStageContext context) {
                return new MaterialAiStageResult(
                        MaterialAiStageType.CONTENT_ANALYSIS,
                        aiResult,
                        List.of(highlight),
                        "Folder"
                );
            }
        };
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(folderService.getOwnedFolder(USER_ID, FOLDER_ID)).thenReturn(folder);
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> {
            Material material = invocation.getArgument(0);
            ReflectionTestUtils.setField(material, "id", 100L);
            return material;
        });
        when(stageRegistry.stagesInOrder()).thenReturn(List.of(stage));
        when(persistenceService.saveAnalysisResult(any(Material.class), any(MaterialAiAnalysisResult.class)))
                .thenReturn(analysis);

        MaterialAiAnalysisPipelineResult result = orchestrator.analyze(preparationResult());

        assertThat(result.materialId()).isEqualTo(100L);
        assertThat(result.materialAnalysisId()).isEqualTo(200L);
        assertThat(result.extractedContent().content()).isEqualTo("source content for chunk later");
        assertThat(result.highlights()).containsExactly(highlight);
        assertThat(result.recommendedFolderName()).isEqualTo("Folder");
        ArgumentCaptor<Material> materialCaptor = ArgumentCaptor.forClass(Material.class);
        verify(materialRepository).save(materialCaptor.capture());
        assertThat(materialCaptor.getValue().getAiStatus()).isEqualTo(AiStatus.ANALYZING);
    }

    @Test
    void propagatesStageFailureForTransactionRollback() {
        User user = user();
        Folder folder = folder(user);
        MaterialAiAnalysisStage failingStage = new MaterialAiAnalysisStage() {
            @Override
            public MaterialAiStageType type() {
                return MaterialAiStageType.CONTENT_ANALYSIS;
            }

            @Override
            public MaterialAiStageResult execute(MaterialAiStageContext context) {
                throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
            }
        };
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(folderService.getOwnedFolder(USER_ID, FOLDER_ID)).thenReturn(folder);
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stageRegistry.stagesInOrder()).thenReturn(List.of(failingStage));

        assertThatThrownBy(() -> orchestrator.analyze(preparationResult()))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
        verify(persistenceService, never()).saveAnalysisResult(any(), any());
    }

    @Test
    void analyzeMethodHasTransactionalRollbackBoundary() throws Exception {
        Method method = MaterialAiAnalysisOrchestrator.class.getMethod(
                "analyze",
                MaterialAnalysisPreparationResult.class
        );

        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
        assertThat(method.getAnnotation(Transactional.class).noRollbackFor()).isEmpty();
    }

    private MaterialAnalysisPreparationResult preparationResult() {
        ExtractedMaterialContent content = new ExtractedMaterialContent(
                "https://example.com",
                PlatformType.BLOG,
                "Extracted Title",
                "source content for chunk later",
                null,
                "author",
                LocalDateTime.of(2026, 7, 24, 10, 0)
        );
        return new MaterialAnalysisPreparationResult(
                USER_ID,
                FOLDER_ID,
                "https://example.com",
                PlatformType.BLOG,
                content
        );
    }

    private User user() {
        User user = User.create("user@example.com", "user", null, null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private Folder folder(User user) {
        Folder folder = Folder.create(user, "Folder");
        ReflectionTestUtils.setField(folder, "id", FOLDER_ID);
        return folder;
    }
}
