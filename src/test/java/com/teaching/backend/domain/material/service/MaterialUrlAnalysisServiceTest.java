package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.service.FolderService;
import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.dto.extract.MaterialAnalysisPreparationResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisPipelineResult;
import com.teaching.backend.domain.material.dto.request.MaterialAnalyzeRequest;
import com.teaching.backend.domain.material.dto.response.MaterialAnalyzeResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.MaterialAnalyzeResultType;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialChunkRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.material.service.ai.MaterialAiAnalysisOrchestrator;
import com.teaching.backend.domain.material.service.extract.MaterialContentExtractorRegistry;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialUrlAnalysisServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;
    private static final String URL = "https://velog.io/@example/spring";

    @Mock
    private FolderService folderService;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialAnalysisRepository materialAnalysisRepository;

    @Mock
    private MaterialChunkRepository materialChunkRepository;

    @Mock
    private MaterialUrlValidator materialUrlValidator;

    @Mock
    private MaterialPlatformResolver materialPlatformResolver;

    @Mock
    private MaterialContentExtractorRegistry materialContentExtractorRegistry;

    @Mock
    private MaterialAiAnalysisOrchestrator materialAiAnalysisOrchestrator;

    @Mock
    private MaterialUrlAnalysisConcurrencyGuard materialUrlAnalysisConcurrencyGuard;

    @InjectMocks
    private MaterialUrlAnalysisService materialUrlAnalysisService;

    @Test
    void returnsAlreadyAnalyzedWhenCompletedMaterialExistsAndForceAnalyzeFalse() {
        Material newerFailed = material(102L, "Failed", URL, PlatformType.VELOG, AiStatus.FAILED, createdAt(2));
        Material olderCompleted = material(101L, "Completed", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(1));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(newerFailed, olderCompleted));
        when(materialAnalysisRepository.findByMaterialId(101L))
                .thenReturn(Optional.of(materialAnalysis(301L, olderCompleted)));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(101L))
                .thenReturn(List.of(
                        materialChunk(olderCompleted, 0),
                        materialChunk(olderCompleted, 1),
                        materialChunk(olderCompleted, 2)
                ));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ALREADY_ANALYZED);
        assertThat(result.existingMaterialId()).isEqualTo(101L);
        assertThat(result.materialId()).isNull();
        assertThat(result.title()).isEqualTo("Completed");
        assertThat(result.originalUrl()).isEqualTo(URL);
        assertThat(result.platformType()).isEqualTo("VELOG");
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.materialAnalysisId()).isEqualTo(301L);
        assertThat(result.chunkCount()).isEqualTo(3);
        verify(materialContentExtractorRegistry, never()).extract(any(), anyString());
        verify(materialAiAnalysisOrchestrator, never()).analyze(any());
    }

    @Test
    void alreadyAnalyzedResponseAllowsMissingAnalysisAndChunksForLegacyData() {
        Material completed = material(101L, "Completed", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(1));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(completed));
        when(materialAnalysisRepository.findByMaterialId(101L)).thenReturn(Optional.empty());
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(101L)).thenReturn(List.of());

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ALREADY_ANALYZED);
        assertThat(result.existingMaterialId()).isEqualTo(101L);
        assertThat(result.materialId()).isNull();
        assertThat(result.materialAnalysisId()).isNull();
        assertThat(result.chunkCount()).isZero();
        verify(materialContentExtractorRegistry, never()).extract(any(), anyString());
        verify(materialAiAnalysisOrchestrator, never()).analyze(any());
    }

    @Test
    void selectsLatestCompletedMaterialWhenDuplicatesExist() {
        Material olderCompleted = material(101L, "Older", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(1));
        Material newerCompleted = material(102L, "Newer", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(2));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(olderCompleted, newerCompleted));
        when(materialAnalysisRepository.findByMaterialId(102L)).thenReturn(Optional.empty());
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(102L)).thenReturn(List.of());

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        );

        assertThat(result.existingMaterialId()).isEqualTo(102L);
        assertThat(result.materialId()).isNull();
        assertThat(result.title()).isEqualTo("Newer");
        verify(materialContentExtractorRegistry, never()).extract(any(), anyString());
        verify(materialAiAnalysisOrchestrator, never()).analyze(any());
    }

    @Test
    void forceAnalyzeTrueReturnsAnalysisRequiredWithoutReusingExistingMaterial() {
        Material completed = material(101L, "Completed", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(1));
        givenValidRequest();
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, true)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        assertThat(result.existingMaterialId()).isNull();
        assertThat(result.materialId()).isEqualTo(200L);
        assertThat(result.materialAnalysisId()).isEqualTo(300L);
        assertThat(result.originalUrl()).isEqualTo(URL);
        assertThat(result.platformType()).isEqualTo("VELOG");
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.chunkCount()).isEqualTo(2);
        verify(materialUrlAnalysisConcurrencyGuard, never()).executeSerialized(any(), anyString(), any(), any());
        verify(materialContentExtractorRegistry).extract(PlatformType.VELOG, URL);
        verify(materialAiAnalysisOrchestrator).analyze(any(MaterialAnalysisPreparationResult.class));
        verify(materialRepository, never()).save(any(Material.class));
    }

    @Test
    void runsFullPipelineWhenNoCompletedMaterialExists() {
        Material failed = material(101L, "Failed", URL, PlatformType.VELOG, AiStatus.FAILED, createdAt(1));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(failed));
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        assertThat(result.existingMaterialId()).isNull();
        assertThat(result.materialId()).isEqualTo(200L);
        assertThat(result.materialAnalysisId()).isEqualTo(300L);
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(materialContentExtractorRegistry).extract(PlatformType.VELOG, URL);
        verify(materialAiAnalysisOrchestrator).analyze(any(MaterialAnalysisPreparationResult.class));
    }

    @Test
    void forceAnalyzeFalseRunsDuplicateLookupAndPipelineInsideConcurrencyGuard() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();

        materialUrlAnalysisService.analyze(USER_ID, new MaterialAnalyzeRequest(URL, FOLDER_ID, false));

        verify(materialUrlAnalysisConcurrencyGuard).executeSerialized(eq(USER_ID), eq(URL), any(), any());
        verify(materialRepository).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL);
        verify(materialAiAnalysisOrchestrator).analyze(any(MaterialAnalysisPreparationResult.class));
    }

    @Test
    void sequentialSameUrlForceAnalyzeFalseReusesCompletedAfterFirstPipeline() {
        Material completed = material(200L, "Title", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(2));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of())
                .thenReturn(List.of(completed));
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();
        when(materialAnalysisRepository.findByMaterialId(200L)).thenReturn(Optional.of(materialAnalysis(300L, completed)));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(200L))
                .thenReturn(List.of(materialChunk(completed, 0), materialChunk(completed, 1)));

        MaterialAnalyzeResponse first = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        );
        MaterialAnalyzeResponse second = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        );

        assertThat(first.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        assertThat(second.resultType()).isEqualTo(MaterialAnalyzeResultType.ALREADY_ANALYZED);
        assertThat(second.existingMaterialId()).isEqualTo(200L);
        assertThat(second.materialId()).isNull();
        assertThat(second.materialAnalysisId()).isEqualTo(300L);
        assertThat(second.chunkCount()).isEqualTo(2);
        verify(materialAiAnalysisOrchestrator, times(1)).analyze(any(MaterialAnalysisPreparationResult.class));
        verify(materialContentExtractorRegistry, times(1)).extract(PlatformType.VELOG, URL);
    }

    @Test
    void releasesConcurrencyGuardOnFailedAnalysisAndAllowsRetry() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(materialContentExtractorRegistry.extract(PlatformType.VELOG, URL))
                .thenThrow(new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED))
                .thenReturn(extractedContent());
        givenSuccessfulPipeline();

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);

        MaterialAnalyzeResponse retry = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        );

        assertThat(retry.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        verify(materialUrlAnalysisConcurrencyGuard, times(2)).executeSerialized(eq(USER_ID), eq(URL), any(), any());
        verify(materialAiAnalysisOrchestrator, times(1)).analyze(any(MaterialAnalysisPreparationResult.class));
    }

    @Test
    void trimsUrlBeforeValidationResolveAndDuplicateLookup() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest("  " + URL + "  ", FOLDER_ID, null)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        assertThat(result.originalUrl()).isEqualTo(URL);
        verify(materialUrlValidator).isValidHttpUrl(URL);
        verify(materialPlatformResolver).resolve(null, URL);
        verify(materialRepository).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL);
        verify(materialContentExtractorRegistry).extract(PlatformType.VELOG, URL);
    }

    @Test
    void validatesOwnedFolderBeforeDuplicateLookup() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();

        materialUrlAnalysisService.analyze(USER_ID, new MaterialAnalyzeRequest(URL, FOLDER_ID, false));

        verify(folderService).getOwnedFolder(USER_ID, FOLDER_ID);
    }

    @Test
    void propagatesFolderNotFoundFromFolderService() {
        when(materialUrlValidator.isValidHttpUrl(URL)).thenReturn(true);
        when(folderService.getOwnedFolder(USER_ID, FOLDER_ID))
                .thenThrow(new FolderException(FolderErrorCode.FOLDER_NOT_FOUND));

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        ))
                .isInstanceOf(FolderException.class)
                .extracting("errorCode")
                .isEqualTo(FolderErrorCode.FOLDER_NOT_FOUND);
        verify(materialRepository, never()).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(any(), anyString());
        verify(materialContentExtractorRegistry, never()).extract(any(), anyString());
    }

    @Test
    void propagatesFolderAccessDeniedFromFolderService() {
        when(materialUrlValidator.isValidHttpUrl(URL)).thenReturn(true);
        when(folderService.getOwnedFolder(USER_ID, FOLDER_ID))
                .thenThrow(new FolderException(FolderErrorCode.FOLDER_ACCESS_DENIED));

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        ))
                .isInstanceOf(FolderException.class)
                .extracting("errorCode")
                .isEqualTo(FolderErrorCode.FOLDER_ACCESS_DENIED);
    }

    @Test
    void rejectsInvalidFolderId() {
        when(materialUrlValidator.isValidHttpUrl(URL)).thenReturn(true);

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, 0L, false)
        ))
                .isInstanceOf(FolderException.class)
                .extracting("errorCode")
                .isEqualTo(FolderErrorCode.INVALID_FOLDER_ID);
        verify(folderService, never()).getOwnedFolder(any(), any());
    }

    @Test
    void rejectsNullUrlAsOriginalUrlRequired() {
        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(null, FOLDER_ID, false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.ORIGINAL_URL_REQUIRED);
        verify(materialUrlValidator, never()).isValidHttpUrl(anyString());
    }

    @Test
    void rejectsBlankUrlAsOriginalUrlRequired() {
        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest("   ", FOLDER_ID, false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.ORIGINAL_URL_REQUIRED);
    }

    @Test
    void rejectsInvalidHttpUrlAsBadRequestBeforeFolderAndDuplicateLookup() {
        when(materialUrlValidator.isValidHttpUrl("ftp://example.com")).thenReturn(false);

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest("ftp://example.com", FOLDER_ID, false)
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.BAD_REQUEST);
        verify(folderService, never()).getOwnedFolder(any(), any());
        verify(materialPlatformResolver, never()).resolve(any(), anyString());
        verify(materialRepository, never()).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(any(), anyString());
        verify(materialContentExtractorRegistry, never()).extract(any(), anyString());
    }

    @Test
    void repositoryLookupUsesCurrentUserId() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();

        materialUrlAnalysisService.analyze(USER_ID, new MaterialAnalyzeRequest(URL, FOLDER_ID, false));

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(materialRepository).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(
                userIdCaptor.capture(),
                anyString()
        );
        assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
    }

    @Test
    void passesPreparationResultToPipelineWithoutExternalRoundTrip() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        ExtractedMaterialContent extractedContent = extractedContent();
        when(materialContentExtractorRegistry.extract(PlatformType.VELOG, URL)).thenReturn(extractedContent);
        when(materialAiAnalysisOrchestrator.analyze(any(MaterialAnalysisPreparationResult.class)))
                .thenReturn(pipelineResult(extractedContent));

        materialUrlAnalysisService.analyze(USER_ID, new MaterialAnalyzeRequest(URL, FOLDER_ID, false));

        ArgumentCaptor<MaterialAnalysisPreparationResult> captor =
                ArgumentCaptor.forClass(MaterialAnalysisPreparationResult.class);
        verify(materialContentExtractorRegistry, times(1)).extract(PlatformType.VELOG, URL);
        verify(materialAiAnalysisOrchestrator).analyze(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().folderId()).isEqualTo(FOLDER_ID);
        assertThat(captor.getValue().originalUrl()).isEqualTo(URL);
        assertThat(captor.getValue().extractedContent()).isEqualTo(extractedContent);
        assertThat(captor.getValue().extractedContent().content())
                .isEqualTo("Extracted content for next analysis bundle");
    }

    @Test
    void prepareAnalysisReturnsExtractedContentForNextAnalysisBundle() {
        ExtractedMaterialContent extractedContent = extractedContent();
        when(materialContentExtractorRegistry.extract(PlatformType.VELOG, URL)).thenReturn(extractedContent);

        MaterialAnalysisPreparationResult result = materialUrlAnalysisService.prepareAnalysis(
                USER_ID,
                FOLDER_ID,
                URL,
                PlatformType.VELOG
        );

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.folderId()).isEqualTo(FOLDER_ID);
        assertThat(result.originalUrl()).isEqualTo(URL);
        assertThat(result.platformType()).isEqualTo(PlatformType.VELOG);
        assertThat(result.extractedContent()).isEqualTo(extractedContent);
    }

    @Test
    void propagatesExtractionFailureOnAnalysisRequiredPath() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        when(materialContentExtractorRegistry.extract(PlatformType.VELOG, URL))
                .thenThrow(new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED));

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        verify(materialRepository, never()).save(any(Material.class));
        verify(materialAiAnalysisOrchestrator, never()).analyze(any());
    }

    @Test
    void propagatesPipelineFailureAfterExtraction() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        givenSuccessfulExtraction();
        when(materialAiAnalysisOrchestrator.analyze(any(MaterialAnalysisPreparationResult.class)))
                .thenThrow(new MaterialException(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED));

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
        verify(materialContentExtractorRegistry).extract(PlatformType.VELOG, URL);
    }

    private void givenValidRequest() {
        when(materialUrlValidator.isValidHttpUrl(URL)).thenReturn(true);
        when(folderService.getOwnedFolder(USER_ID, FOLDER_ID)).thenReturn(folder(USER_ID, FOLDER_ID));
        when(materialPlatformResolver.resolve(null, URL)).thenReturn(PlatformType.VELOG);
        lenient().when(materialUrlAnalysisConcurrencyGuard.executeSerialized(eq(USER_ID), eq(URL), any(), any()))
                .thenAnswer(invocation -> {
                    Optional<?> completed = (Optional<?>) invocation.getArgument(2, Supplier.class).get();
                    if (completed.isPresent()) {
                        return completed.get();
                    }
                    return invocation.getArgument(3, Supplier.class).get();
                });
    }

    private void givenSuccessfulExtraction() {
        when(materialContentExtractorRegistry.extract(PlatformType.VELOG, URL)).thenReturn(extractedContent());
    }

    private void givenSuccessfulPipeline() {
        when(materialAiAnalysisOrchestrator.analyze(any(MaterialAnalysisPreparationResult.class)))
                .thenAnswer(invocation -> pipelineResult(invocation.getArgument(0, MaterialAnalysisPreparationResult.class)
                        .extractedContent()));
    }

    private MaterialAiAnalysisPipelineResult pipelineResult(ExtractedMaterialContent extractedContent) {
        return new MaterialAiAnalysisPipelineResult(
                200L,
                USER_ID,
                URL,
                PlatformType.VELOG,
                extractedContent,
                300L,
                2,
                List.of(),
                null,
                null
        );
    }

    private ExtractedMaterialContent extractedContent() {
        return new ExtractedMaterialContent(
                URL,
                PlatformType.VELOG,
                "Title",
                "Extracted content for next analysis bundle",
                "https://example.com/thumb.jpg",
                "author",
                createdAt(1)
        );
    }

    private Material material(
            Long materialId,
            String title,
            String originalUrl,
            PlatformType platformType,
            AiStatus aiStatus,
            LocalDateTime createdAt
    ) {
        User user = user(USER_ID);
        Folder folder = folder(user.getId(), FOLDER_ID);
        Material material = Material.create(user, folder, title, originalUrl, platformType);
        ReflectionTestUtils.setField(material, "id", materialId);
        ReflectionTestUtils.setField(material, "aiStatus", aiStatus);
        ReflectionTestUtils.setField(material, "createdAt", createdAt);
        return material;
    }

    private MaterialAnalysis materialAnalysis(Long analysisId, Material material) {
        MaterialAnalysis analysis = MaterialAnalysis.create(material, "summary", "detail", "v1");
        ReflectionTestUtils.setField(analysis, "id", analysisId);
        return analysis;
    }

    private MaterialChunk materialChunk(Material material, int chunkIndex) {
        return MaterialChunk.create(
                material,
                chunkIndex,
                "chunk " + chunkIndex,
                "point-" + chunkIndex,
                "chunk-" + chunkIndex
        );
    }

    private Folder folder(Long userId, Long folderId) {
        Folder folder = Folder.create(user(userId), "Folder");
        ReflectionTestUtils.setField(folder, "id", folderId);
        return folder;
    }

    private User user(Long userId) {
        User user = User.create("user" + userId + "@example.com", "user" + userId, null, null, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private LocalDateTime createdAt(int day) {
        return LocalDateTime.of(2026, 7, day, 10, 0);
    }
}
