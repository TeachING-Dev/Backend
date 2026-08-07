package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisPipelineResult;
import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.dto.extract.MaterialAnalysisPreparationResult;
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
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.entity.Tag;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialUrlAnalysisServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;
    private static final Long RECOMMENDED_FOLDER_ID = 20L;
    private static final String URL = "https://velog.io/@example/spring";
    private static final String YOUTUBE_URL = "https://www.youtube.com/watch?v=video";
    private static final String WEB_URL = "https://www.hanbit.co.kr/channel/view.html?cmscode=CMS7876574876";
    private static final String TISTORY_URL = "https://example.tistory.com/post";
    private static final String NAVER_BLOG_URL = "https://blog.naver.com/writer/123";
    private static final String BLOG_URL = "https://medium.com/example/post";

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

    @Mock
    private MaterialTagRepository materialTagRepository;

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
        when(materialTagRepository.findAllByMaterialId(101L))
                .thenReturn(List.of(materialTag(olderCompleted, 501L, "Spring")));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, false)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ALREADY_ANALYZED);
        assertThat(result.existingMaterialId()).isEqualTo(101L);
        assertThat(result.existingFolderId()).isEqualTo(FOLDER_ID);
        assertThat(result.materialId()).isNull();
        assertThat(result.title()).isEqualTo("Completed");
        assertThat(result.originalUrl()).isEqualTo(URL);
        assertThat(result.platformType()).isEqualTo("VELOG");
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.materialAnalysisId()).isEqualTo(301L);
        assertThat(result.chunkCount()).isEqualTo(3);
        assertThat(result.tags()).singleElement()
                .satisfies(tag -> {
                    assertThat(tag.tagId()).isEqualTo(501L);
                    assertThat(tag.tagName()).isEqualTo("Spring");
                });
        verify(materialContentExtractorRegistry, never()).extract(any(), anyString());
        verify(materialAiAnalysisOrchestrator, never()).analyze(any(), any());
    }

    @Test
    void alreadyAnalyzedResponseAllowsMissingAnalysisAndChunksForLegacyData() {
        Material completed = material(101L, "Completed", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(1));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(completed));
        when(materialAnalysisRepository.findByMaterialId(101L)).thenReturn(Optional.empty());
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(101L)).thenReturn(List.of());
        when(materialTagRepository.findAllByMaterialId(101L)).thenReturn(List.of());

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, false)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ALREADY_ANALYZED);
        assertThat(result.existingMaterialId()).isEqualTo(101L);
        assertThat(result.existingFolderId()).isEqualTo(FOLDER_ID);
        assertThat(result.materialId()).isNull();
        assertThat(result.materialAnalysisId()).isNull();
        assertThat(result.chunkCount()).isZero();
        assertThat(result.tags()).isEmpty();
        verify(materialContentExtractorRegistry, never()).extract(any(), anyString());
        verify(materialAiAnalysisOrchestrator, never()).analyze(any(), any());
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
        when(materialTagRepository.findAllByMaterialId(102L)).thenReturn(List.of());

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, false)
        );

        assertThat(result.existingMaterialId()).isEqualTo(102L);
        assertThat(result.existingFolderId()).isEqualTo(FOLDER_ID);
        assertThat(result.materialId()).isNull();
        assertThat(result.title()).isEqualTo("Newer");
        verify(materialContentExtractorRegistry, never()).extract(any(), anyString());
        verify(materialAiAnalysisOrchestrator, never()).analyze(any(), any());
    }

    @Test
    void forceAnalyzeTrueRunsNewPipelineSerializedThroughConcurrencyGuard() {
        givenValidRequest();
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();
        when(materialTagRepository.findAllByMaterialId(200L))
                .thenReturn(List.of(materialTag(null, 501L, "Spring")));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, true)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        assertThat(result.existingMaterialId()).isNull();
        assertThat(result.materialId()).isEqualTo(200L);
        assertThat(result.materialAnalysisId()).isEqualTo(300L);
        assertThat(result.originalUrl()).isEqualTo(URL);
        assertThat(result.platformType()).isEqualTo("VELOG");
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.recommendedFolderId()).isEqualTo(RECOMMENDED_FOLDER_ID);
        assertThat(result.recommendedFolderName()).isEqualTo("Backend");
        assertThat(result.tags()).singleElement()
                .satisfies(tag -> assertThat(tag.tagName()).isEqualTo("Spring"));
        // 재사용 도입(#114) 이후로는 forceAnalyze도 같은 URL에 대한 동시 재분석이 같은 자료를 동시에
        // 갱신하지 않도록 concurrency guard로 직렬화해야 한다.
        verify(materialUrlAnalysisConcurrencyGuard).executeSerialized(eq(USER_ID), eq(URL), any(), any());
        verify(materialContentExtractorRegistry).extract(PlatformType.VELOG, URL);
        verify(materialAiAnalysisOrchestrator).analyze(any(MaterialAnalysisPreparationResult.class), isNull());
    }

    // 재분석(forceAnalyze=true)이 같은 URL로 이미 분석 완료된 자료를 찾으면, 새 자료를 만들지 않고
    // 그 기존 자료를 재사용해야 한다(#114) — 안 그러면 재분석할 때마다 자료/청크/벡터가 중복으로 쌓인다.
    @Test
    void forceAnalyzeTrueReusesExistingCompletedMaterialInsteadOfCreatingNew() {
        Material completed = material(101L, "Completed", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(1));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(completed));
        givenSuccessfulExtraction();
        when(materialAiAnalysisOrchestrator.analyze(any(MaterialAnalysisPreparationResult.class), eq(completed)))
                .thenAnswer(invocation -> pipelineResult(
                        invocation.getArgument(0, MaterialAnalysisPreparationResult.class).extractedContent()
                ));
        when(materialTagRepository.findAllByMaterialId(200L)).thenReturn(List.of());

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, true)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        assertThat(result.existingMaterialId()).isEqualTo(101L);
        verify(materialAiAnalysisOrchestrator).analyze(any(MaterialAnalysisPreparationResult.class), eq(completed));
        verify(materialAiAnalysisOrchestrator, never()).analyze(any(MaterialAnalysisPreparationResult.class), isNull());
    }

    @Test
    void runsFullPipelineWhenNoCompletedMaterialExists() {
        Material failed = material(101L, "Failed", URL, PlatformType.VELOG, AiStatus.FAILED, createdAt(1));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(failed));
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();
        when(materialTagRepository.findAllByMaterialId(200L))
                .thenReturn(List.of(materialTag(null, 501L, "Spring"), materialTag(null, 502L, "JPA")));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, false)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        assertThat(result.existingMaterialId()).isNull();
        assertThat(result.materialId()).isEqualTo(200L);
        assertThat(result.materialAnalysisId()).isEqualTo(300L);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.recommendedFolderId()).isEqualTo(RECOMMENDED_FOLDER_ID);
        assertThat(result.recommendedFolderName()).isEqualTo("Backend");
        assertThat(result.tags()).extracting("tagName").containsExactly("Spring", "JPA");
        verify(materialContentExtractorRegistry).extract(PlatformType.VELOG, URL);
        verify(materialAiAnalysisOrchestrator).analyze(any(MaterialAnalysisPreparationResult.class), isNull());
    }

    @Test
    void forceAnalyzeFalseRunsDuplicateLookupAndPipelineInsideConcurrencyGuard() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();
        when(materialTagRepository.findAllByMaterialId(200L)).thenReturn(List.of());

        materialUrlAnalysisService.analyze(USER_ID, new MaterialAnalyzeRequest(URL, false));

        verify(materialUrlAnalysisConcurrencyGuard).executeSerialized(eq(USER_ID), eq(URL), any(), any());
        verify(materialRepository).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL);
        verify(materialAiAnalysisOrchestrator).analyze(any(MaterialAnalysisPreparationResult.class), isNull());
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
        when(materialTagRepository.findAllByMaterialId(200L))
                .thenReturn(List.of(materialTag(completed, 501L, "Spring")));

        MaterialAnalyzeResponse first = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, false)
        );
        MaterialAnalyzeResponse second = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, false)
        );

        assertThat(first.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        assertThat(second.resultType()).isEqualTo(MaterialAnalyzeResultType.ALREADY_ANALYZED);
        assertThat(second.existingMaterialId()).isEqualTo(200L);
        assertThat(second.existingFolderId()).isEqualTo(FOLDER_ID);
        assertThat(second.materialId()).isNull();
        assertThat(second.materialAnalysisId()).isEqualTo(300L);
        assertThat(second.chunkCount()).isEqualTo(2);
        assertThat(second.tags()).singleElement()
                .satisfies(tag -> assertThat(tag.tagName()).isEqualTo("Spring"));
        verify(materialAiAnalysisOrchestrator, times(1)).analyze(any(MaterialAnalysisPreparationResult.class), isNull());
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
        when(materialTagRepository.findAllByMaterialId(200L)).thenReturn(List.of());

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);

        MaterialAnalyzeResponse retry = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, false)
        );

        assertThat(retry.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        verify(materialUrlAnalysisConcurrencyGuard, times(2)).executeSerialized(eq(USER_ID), eq(URL), any(), any());
        verify(materialAiAnalysisOrchestrator, times(1)).analyze(any(MaterialAnalysisPreparationResult.class), isNull());
    }

    @Test
    void trimsUrlBeforeValidationResolveAndDuplicateLookup() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        givenSuccessfulExtraction();
        givenSuccessfulPipeline();
        when(materialTagRepository.findAllByMaterialId(200L)).thenReturn(List.of());

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest("  " + URL + "  ", null)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_COMPLETED);
        assertThat(result.originalUrl()).isEqualTo(URL);
        verify(materialUrlValidator).isValidHttpUrl(URL);
        verify(materialPlatformResolver).resolve(null, URL);
        verify(materialRepository).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL);
        verify(materialContentExtractorRegistry).extract(PlatformType.VELOG, URL);
    }

    @Test
    void rejectsNullUrlAsOriginalUrlRequired() {
        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(null, false)
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
                new MaterialAnalyzeRequest("   ", false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.ORIGINAL_URL_REQUIRED);
    }

    @Test
    void rejectsInvalidHttpUrlAsBadRequestBeforeDuplicateLookup() {
        when(materialUrlValidator.isValidHttpUrl("ftp://example.com")).thenReturn(false);

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest("ftp://example.com", false)
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.BAD_REQUEST);
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
        when(materialTagRepository.findAllByMaterialId(200L)).thenReturn(List.of());

        materialUrlAnalysisService.analyze(USER_ID, new MaterialAnalyzeRequest(URL, false));

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
        when(materialAiAnalysisOrchestrator.analyze(any(MaterialAnalysisPreparationResult.class), isNull()))
                .thenReturn(pipelineResult(extractedContent));
        when(materialTagRepository.findAllByMaterialId(200L)).thenReturn(List.of());

        materialUrlAnalysisService.analyze(USER_ID, new MaterialAnalyzeRequest(URL, false));

        ArgumentCaptor<MaterialAnalysisPreparationResult> captor =
                ArgumentCaptor.forClass(MaterialAnalysisPreparationResult.class);
        verify(materialContentExtractorRegistry, times(1)).extract(PlatformType.VELOG, URL);
        verify(materialAiAnalysisOrchestrator).analyze(captor.capture(), isNull());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().folderId()).isNull();
        assertThat(captor.getValue().originalUrl()).isEqualTo(URL);
        assertThat(captor.getValue().extractedContent()).isEqualTo(extractedContent);
        assertThat(captor.getValue().extractedContent().content())
                .isEqualTo("Extracted content for next analysis bundle");
    }

    @Test
    void passesYoutubeTranscriptContentToExistingPipeline() {
        ExtractedMaterialContent youtubeContent = new ExtractedMaterialContent(
                YOUTUBE_URL,
                PlatformType.YOUTUBE,
                "Video Title",
                "YouTube transcript text from official captions",
                "https://example.com/youtube-thumb.jpg",
                "channel",
                createdAt(1)
        );
        when(materialUrlValidator.isValidHttpUrl(YOUTUBE_URL)).thenReturn(true);
        when(materialPlatformResolver.resolve(null, YOUTUBE_URL)).thenReturn(PlatformType.YOUTUBE);
        when(materialUrlAnalysisConcurrencyGuard.executeSerialized(eq(USER_ID), eq(YOUTUBE_URL), any(), any()))
                .thenAnswer(invocation -> {
                    Optional<?> completed = (Optional<?>) invocation.getArgument(2, Supplier.class).get();
                    if (completed.isPresent()) {
                        return completed.get();
                    }
                    return invocation.getArgument(3, Supplier.class).get();
                });
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, YOUTUBE_URL))
                .thenReturn(List.of());
        when(materialContentExtractorRegistry.extract(PlatformType.YOUTUBE, YOUTUBE_URL)).thenReturn(youtubeContent);
        when(materialAiAnalysisOrchestrator.analyze(any(MaterialAnalysisPreparationResult.class), isNull()))
                .thenAnswer(invocation -> {
                    MaterialAnalysisPreparationResult preparationResult = invocation.getArgument(
                            0,
                            MaterialAnalysisPreparationResult.class
                    );
                    return new MaterialAiAnalysisPipelineResult(
                            200L,
                            USER_ID,
                            null,
                            "Summary",
                            YOUTUBE_URL,
                            PlatformType.YOUTUBE,
                            preparationResult.extractedContent(),
                            300L,
                            1,
                            RECOMMENDED_FOLDER_ID,
                            "Backend",
                            List.of("YouTube")
                    );
                });
        when(materialTagRepository.findAllByMaterialId(200L))
                .thenReturn(List.of(materialTag(null, 501L, "YouTube")));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(YOUTUBE_URL, false)
        );

        ArgumentCaptor<MaterialAnalysisPreparationResult> captor =
                ArgumentCaptor.forClass(MaterialAnalysisPreparationResult.class);
        verify(materialContentExtractorRegistry).extract(PlatformType.YOUTUBE, YOUTUBE_URL);
        verify(materialAiAnalysisOrchestrator).analyze(captor.capture(), isNull());
        assertThat(captor.getValue().platformType()).isEqualTo(PlatformType.YOUTUBE);
        assertThat(captor.getValue().folderId()).isNull();
        assertThat(captor.getValue().extractedContent().content())
                .isEqualTo("YouTube transcript text from official captions");
        assertThat(result.platformType()).isEqualTo("YOUTUBE");
        assertThat(result.tags()).singleElement()
                .satisfies(tag -> assertThat(tag.tagName()).isEqualTo("YouTube"));
    }

    @Test
    void keepsGenericWebPlatformTypeFromExtractionThroughResponse() {
        ExtractedMaterialContent webContent = new ExtractedMaterialContent(
                WEB_URL,
                PlatformType.WEB,
                "Hanbit Article",
                "Generic web article content for downstream analysis",
                null,
                "author",
                createdAt(1)
        );
        when(materialUrlValidator.isValidHttpUrl(WEB_URL)).thenReturn(true);
        when(materialPlatformResolver.resolve(null, WEB_URL)).thenReturn(PlatformType.WEB);
        when(materialUrlAnalysisConcurrencyGuard.executeSerialized(eq(USER_ID), eq(WEB_URL), any(), any()))
                .thenAnswer(invocation -> {
                    Optional<?> completed = (Optional<?>) invocation.getArgument(2, Supplier.class).get();
                    if (completed.isPresent()) {
                        return completed.get();
                    }
                    return invocation.getArgument(3, Supplier.class).get();
                });
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, WEB_URL))
                .thenReturn(List.of());
        when(materialContentExtractorRegistry.extract(PlatformType.WEB, WEB_URL)).thenReturn(webContent);
        when(materialAiAnalysisOrchestrator.analyze(any(MaterialAnalysisPreparationResult.class), isNull()))
                .thenAnswer(invocation -> {
                    MaterialAnalysisPreparationResult preparationResult = invocation.getArgument(
                            0,
                            MaterialAnalysisPreparationResult.class
                    );
                    return new MaterialAiAnalysisPipelineResult(
                            201L,
                            USER_ID,
                            preparationResult.folderId(),
                            "Summary",
                            WEB_URL,
                            preparationResult.platformType(),
                            preparationResult.extractedContent(),
                            301L,
                            1,
                            null,
                            null,
                            List.of("Web")
                    );
                });
        when(materialTagRepository.findAllByMaterialId(201L))
                .thenReturn(List.of(materialTag(null, 601L, "Web")));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(WEB_URL, false)
        );

        ArgumentCaptor<MaterialAnalysisPreparationResult> captor =
                ArgumentCaptor.forClass(MaterialAnalysisPreparationResult.class);
        verify(materialContentExtractorRegistry).extract(PlatformType.WEB, WEB_URL);
        verify(materialAiAnalysisOrchestrator).analyze(captor.capture(), isNull());
        assertThat(captor.getValue().platformType()).isEqualTo(PlatformType.WEB);
        assertThat(captor.getValue().extractedContent().platformType()).isEqualTo(PlatformType.WEB);
        assertThat(result.platformType()).isEqualTo("WEB");
    }

    @Test
    void keepsTistoryResolverPlatformTypeThroughResponse() {
        assertAnalyzeKeepsResolverPlatform(TISTORY_URL, PlatformType.TISTORY, PlatformType.BLOG);
    }

    @Test
    void keepsNaverBlogResolverPlatformTypeThroughResponse() {
        assertAnalyzeKeepsResolverPlatform(NAVER_BLOG_URL, PlatformType.NAVER_BLOG, PlatformType.BLOG);
    }

    @Test
    void keepsGeneralBlogResolverPlatformTypeThroughResponse() {
        assertAnalyzeKeepsResolverPlatform(BLOG_URL, PlatformType.BLOG, PlatformType.BLOG);
    }

    @Test
    void prepareAnalysisReturnsExtractedContentWithNullFolderId() {
        ExtractedMaterialContent extractedContent = extractedContent();
        when(materialContentExtractorRegistry.extract(PlatformType.VELOG, URL)).thenReturn(extractedContent);

        MaterialAnalysisPreparationResult result = materialUrlAnalysisService.prepareAnalysis(
                USER_ID,
                URL,
                PlatformType.VELOG
        );

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.folderId()).isNull();
        assertThat(result.originalUrl()).isEqualTo(URL);
        assertThat(result.platformType()).isEqualTo(PlatformType.VELOG);
        assertThat(result.extractedContent()).isEqualTo(extractedContent);
    }

    @Test
    void propagatesExtractionFailureOnPipelinePath() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        when(materialContentExtractorRegistry.extract(PlatformType.VELOG, URL))
                .thenThrow(new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED));

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        verify(materialAiAnalysisOrchestrator, never()).analyze(any(), any());
    }

    @Test
    void propagatesPipelineFailureAfterExtraction() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());
        givenSuccessfulExtraction();
        when(materialAiAnalysisOrchestrator.analyze(any(MaterialAnalysisPreparationResult.class), isNull()))
                .thenThrow(new MaterialException(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED));

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
        verify(materialContentExtractorRegistry).extract(PlatformType.VELOG, URL);
    }

    private void givenValidRequest() {
        when(materialUrlValidator.isValidHttpUrl(URL)).thenReturn(true);
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
        when(materialAiAnalysisOrchestrator.analyze(any(MaterialAnalysisPreparationResult.class), isNull()))
                .thenAnswer(invocation -> pipelineResult(invocation.getArgument(0, MaterialAnalysisPreparationResult.class)
                        .extractedContent()));
    }

    private MaterialAiAnalysisPipelineResult pipelineResult(ExtractedMaterialContent extractedContent) {
        return new MaterialAiAnalysisPipelineResult(
                200L,
                USER_ID,
                null,
                "Summary",
                URL,
                PlatformType.VELOG,
                extractedContent,
                300L,
                2,
                RECOMMENDED_FOLDER_ID,
                "Backend",
                List.of("Spring", "JPA")
        );
    }

    private void assertAnalyzeKeepsResolverPlatform(
            String originalUrl,
            PlatformType resolvedPlatformType,
            PlatformType extractedPlatformType
    ) {
        ExtractedMaterialContent extractedContent = new ExtractedMaterialContent(
                originalUrl,
                extractedPlatformType,
                "Title",
                "Extracted content for platform split analysis",
                null,
                "author",
                createdAt(1)
        );
        when(materialUrlValidator.isValidHttpUrl(originalUrl)).thenReturn(true);
        when(materialPlatformResolver.resolve(null, originalUrl)).thenReturn(resolvedPlatformType);
        when(materialUrlAnalysisConcurrencyGuard.executeSerialized(eq(USER_ID), eq(originalUrl), any(), any()))
                .thenAnswer(invocation -> {
                    Optional<?> completed = (Optional<?>) invocation.getArgument(2, Supplier.class).get();
                    if (completed.isPresent()) {
                        return completed.get();
                    }
                    return invocation.getArgument(3, Supplier.class).get();
                });
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, originalUrl))
                .thenReturn(List.of());
        when(materialContentExtractorRegistry.extract(resolvedPlatformType, originalUrl)).thenReturn(extractedContent);
        when(materialAiAnalysisOrchestrator.analyze(any(MaterialAnalysisPreparationResult.class)))
                .thenAnswer(invocation -> {
                    MaterialAnalysisPreparationResult preparationResult = invocation.getArgument(
                            0,
                            MaterialAnalysisPreparationResult.class
                    );
                    return new MaterialAiAnalysisPipelineResult(
                            202L,
                            USER_ID,
                            preparationResult.folderId(),
                            "Summary",
                            originalUrl,
                            preparationResult.platformType(),
                            preparationResult.extractedContent(),
                            302L,
                            1,
                            List.of(),
                            null,
                            null,
                            List.of("Blog")
                    );
                });
        when(materialTagRepository.findAllByMaterialId(202L))
                .thenReturn(List.of(materialTag(null, 701L, "Blog")));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(originalUrl, false)
        );

        ArgumentCaptor<MaterialAnalysisPreparationResult> captor =
                ArgumentCaptor.forClass(MaterialAnalysisPreparationResult.class);
        verify(materialContentExtractorRegistry).extract(resolvedPlatformType, originalUrl);
        verify(materialAiAnalysisOrchestrator).analyze(captor.capture());
        assertThat(captor.getValue().platformType()).isEqualTo(resolvedPlatformType);
        assertThat(captor.getValue().extractedContent().platformType()).isEqualTo(extractedPlatformType);
        assertThat(result.platformType()).isEqualTo(resolvedPlatformType.name());
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
                chunkIndex + 1,
                chunkIndex + 1
        );
    }

    private MaterialTag materialTag(Material material, Long tagId, String tagName) {
        Material targetMaterial = material == null
                ? material(200L, "Title", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(2))
                : material;
        Tag tag = Tag.create(tagName);
        ReflectionTestUtils.setField(tag, "id", tagId);
        return MaterialTag.create(targetMaterial, tag);
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
