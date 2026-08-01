package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.dto.ai.MaterialAiHighlightResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiStageContext;
import com.teaching.backend.domain.material.dto.ai.MaterialAiStageResult;
import com.teaching.backend.domain.material.dto.ai.MaterialUrlAnalysisParseResult;
import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.MaterialAiHighlightType;
import com.teaching.backend.domain.material.enums.MaterialAiStageType;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.service.MaterialAiAnalysisResponseParser;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentAnalysisMaterialAiStageTest {

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private MaterialUrlAnalysisPromptBuilder materialUrlAnalysisPromptBuilder;

    @Mock
    private MaterialAiAnalysisResponseParser materialAiAnalysisResponseParser;

    private OpenAiRetryExecutor openAiRetryExecutor;

    @Mock
    private FolderRepository folderRepository;

    private ContentAnalysisMaterialAiStage stage;

    @BeforeEach
    void setUp() {
        openAiRetryExecutor = new OpenAiRetryExecutor(0);
        stage = new ContentAnalysisMaterialAiStage(
                openAiClient,
                materialUrlAnalysisPromptBuilder,
                materialAiAnalysisResponseParser,
                openAiRetryExecutor,
                folderRepository
        );
    }

    @Test
    void executesUrlAnalysisPromptAndParserWithActiveFolderNames() {
        MaterialAiStageContext context = context();
        MaterialAiAnalysisResult parsed = new MaterialAiAnalysisResult("summary", "detail", List.of("tag"), null, "Backend");
        MaterialAiHighlightResult highlight = new MaterialAiHighlightResult("important", MaterialAiHighlightType.CORE);
        when(folderRepository.findAllByUser_Id(org.mockito.ArgumentMatchers.eq(1L), any(Sort.class)))
                .thenReturn(List.of(folder("Backend"), folder("Backend"), folder("  "), folder("AI")));
        when(materialUrlAnalysisPromptBuilder.buildSystemMessage()).thenReturn("system prompt");
        when(materialUrlAnalysisPromptBuilder.buildUserMessage(
                org.mockito.ArgumentMatchers.eq(context.extractedContent()),
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn("user prompt with source content and Backend");
        when(openAiClient.chatCompleteJson("system prompt", "user prompt with source content and Backend"))
                .thenReturn("raw");
        when(materialAiAnalysisResponseParser.parseUrlAnalysis("raw", List.of("Backend", "AI")))
                .thenReturn(new MaterialUrlAnalysisParseResult(parsed, List.of(highlight), "Backend"));

        MaterialAiStageResult result = stage.execute(context);

        assertThat(result.stageType()).isEqualTo(MaterialAiStageType.CONTENT_ANALYSIS);
        assertThat(result.analysisResult()).isEqualTo(parsed);
        assertThat(result.highlights()).containsExactly(highlight);
        assertThat(result.recommendedFolderName()).isEqualTo("Backend");
        verify(openAiClient).chatCompleteJson("system prompt", "user prompt with source content and Backend");
        verify(materialAiAnalysisResponseParser).parseUrlAnalysis("raw", List.of("Backend", "AI"));

        ArgumentCaptor<List<String>> folderNamesCaptor = ArgumentCaptor.forClass(List.class);
        verify(materialUrlAnalysisPromptBuilder).buildUserMessage(
                org.mockito.ArgumentMatchers.eq(context.extractedContent()),
                folderNamesCaptor.capture()
        );
        assertThat(folderNamesCaptor.getValue()).containsExactly("Backend", "AI");
    }

    @Test
    void parsingFailureRetriesOnceAndThenSucceeds() {
        MaterialAiStageContext context = context();
        MaterialAiAnalysisResult parsed = new MaterialAiAnalysisResult("summary", "detail", List.of("tag"), null, null);
        when(folderRepository.findAllByUser_Id(org.mockito.ArgumentMatchers.eq(1L), any(Sort.class)))
                .thenReturn(List.of(folder("Backend")));
        when(materialUrlAnalysisPromptBuilder.buildSystemMessage()).thenReturn("system");
        when(materialUrlAnalysisPromptBuilder.buildUserMessage(context.extractedContent(), List.of("Backend"))).thenReturn("user");
        when(openAiClient.chatCompleteJson("system", "user"))
                .thenReturn("invalid raw")
                .thenReturn("valid raw");
        when(materialAiAnalysisResponseParser.parseUrlAnalysis("invalid raw", List.of("Backend")))
                .thenThrow(new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED));
        when(materialAiAnalysisResponseParser.parseUrlAnalysis("valid raw", List.of("Backend")))
                .thenReturn(new MaterialUrlAnalysisParseResult(parsed, List.of(), null));

        MaterialAiStageResult result = stage.execute(context);

        assertThat(result.analysisResult()).isEqualTo(parsed);
        verify(openAiClient, times(2)).chatCompleteJson("system", "user");
        verify(materialAiAnalysisResponseParser).parseUrlAnalysis("invalid raw", List.of("Backend"));
        verify(materialAiAnalysisResponseParser).parseUrlAnalysis("valid raw", List.of("Backend"));
    }

    @Test
    void parsingFailureAfterRetryIsPropagated() {
        MaterialAiStageContext context = context();
        when(folderRepository.findAllByUser_Id(org.mockito.ArgumentMatchers.eq(1L), any(Sort.class)))
                .thenReturn(List.of(folder("Backend")));
        when(materialUrlAnalysisPromptBuilder.buildSystemMessage()).thenReturn("system");
        when(materialUrlAnalysisPromptBuilder.buildUserMessage(context.extractedContent(), List.of("Backend"))).thenReturn("user");
        when(openAiClient.chatCompleteJson("system", "user"))
                .thenReturn("invalid raw")
                .thenReturn("still invalid raw");
        when(materialAiAnalysisResponseParser.parseUrlAnalysis("invalid raw", List.of("Backend")))
                .thenThrow(new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED));
        when(materialAiAnalysisResponseParser.parseUrlAnalysis("still invalid raw", List.of("Backend")))
                .thenThrow(new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED));

        assertThatThrownBy(() -> stage.execute(context))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
        verify(openAiClient, times(2)).chatCompleteJson("system", "user");
    }

    @Test
    void openAiCallUsesOneSystemMessageAndOneUserMessage() {
        MaterialAiStageContext context = context();
        MaterialAiAnalysisResult parsed = new MaterialAiAnalysisResult("summary", "detail", List.of("tag"), null, null);
        when(folderRepository.findAllByUser_Id(org.mockito.ArgumentMatchers.eq(1L), any(Sort.class)))
                .thenReturn(List.of(folder("Backend")));
        when(materialUrlAnalysisPromptBuilder.buildSystemMessage()).thenReturn("system-from-resource");
        when(materialUrlAnalysisPromptBuilder.buildUserMessage(context.extractedContent(), List.of("Backend")))
                .thenReturn("user-with-extracted-content");
        when(openAiClient.chatCompleteJson("system-from-resource", "user-with-extracted-content")).thenReturn("raw");
        when(materialAiAnalysisResponseParser.parseUrlAnalysis("raw", List.of("Backend")))
                .thenReturn(new MaterialUrlAnalysisParseResult(parsed, List.of(), null));

        stage.execute(context);

        verify(openAiClient).chatCompleteJson("system-from-resource", "user-with-extracted-content");
    }

    @Test
    void blankExtractedContentFailsBeforeOpenAiCall() {
        MaterialAiStageContext context = new MaterialAiStageContext(
                1L,
                10L,
                "https://example.com",
                PlatformType.BLOG,
                new ExtractedMaterialContent(
                        "https://example.com",
                        PlatformType.BLOG,
                        "Title",
                        " ",
                        null,
                        null,
                        null
                ),
                context().material(),
                Optional.empty()
        );

        assertThatThrownBy(() -> stage.execute(context))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
        verify(openAiClient, never()).chatCompleteJson(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void promptLengthDiagnosticHandlesNullInputsSafely() {
        ContentAnalysisMaterialAiStage.PromptLengthDiagnostic diagnostic =
                ContentAnalysisMaterialAiStage.promptLengthDiagnostic(null, null, null, null);

        assertThat(diagnostic.extractedContentLength()).isZero();
        assertThat(diagnostic.systemPromptLength()).isZero();
        assertThat(diagnostic.userMessageLength()).isZero();
        assertThat(diagnostic.folderCount()).isZero();
        assertThat(diagnostic.folderContextLength()).isZero();
        assertThat(diagnostic.totalPromptLength()).isZero();
        assertThat(diagnostic.platformType()).isNull();
        assertThat(diagnostic.sanitizedUrl()).isNull();
    }

    @Test
    void promptLengthDiagnosticMasksUrlQueryAndCalculatesLengths() {
        ExtractedMaterialContent content = new ExtractedMaterialContent(
                "https://velog.io/@user/post?token=secret",
                PlatformType.VELOG,
                "Title",
                "source content",
                null,
                null,
                LocalDateTime.now()
        );
        MaterialAiStageContext context = new MaterialAiStageContext(
                1L,
                null,
                "https://velog.io/@user/post?token=secret",
                PlatformType.VELOG,
                content,
                null,
                Optional.empty()
        );

        ContentAnalysisMaterialAiStage.PromptLengthDiagnostic diagnostic =
                ContentAnalysisMaterialAiStage.promptLengthDiagnostic(
                        context,
                        List.of("Backend", "AI"),
                        "system",
                        "user message"
                );

        assertThat(diagnostic.extractedContentLength()).isEqualTo("source content".length());
        assertThat(diagnostic.systemPromptLength()).isEqualTo("system".length());
        assertThat(diagnostic.userMessageLength()).isEqualTo("user message".length());
        assertThat(diagnostic.folderCount()).isEqualTo(2);
        assertThat(diagnostic.folderContextLength()).isEqualTo("Backend".length() + "AI".length());
        assertThat(diagnostic.totalPromptLength()).isEqualTo("system".length() + "user message".length());
        assertThat(diagnostic.platformType()).isEqualTo(PlatformType.VELOG);
        assertThat(diagnostic.sanitizedUrl()).isEqualTo("https://velog.io/@user/post");
    }

    private MaterialAiStageContext context() {
        User user = User.create("user@example.com", "user", null, null, null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Folder folder = Folder.create(user, "Folder");
        Material material = Material.create(user, folder, "Title", "https://example.com", PlatformType.BLOG);
        ExtractedMaterialContent content = new ExtractedMaterialContent(
                "https://example.com",
                PlatformType.BLOG,
                "Title",
                "source content",
                null,
                null,
                LocalDateTime.now()
        );
        return new MaterialAiStageContext(
                1L,
                10L,
                "https://example.com",
                PlatformType.BLOG,
                content,
                material,
                Optional.empty()
        );
    }

    private Folder folder(String name) {
        User user = User.create("user@example.com", "user", null, null, null);
        return Folder.create(user, name);
    }
}
