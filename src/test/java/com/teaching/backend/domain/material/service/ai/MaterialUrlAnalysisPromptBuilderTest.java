package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.dto.extract.MaterialImageCandidate;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterialUrlAnalysisPromptBuilderTest {

    private final MaterialUrlAnalysisPromptProvider promptProvider = mock(MaterialUrlAnalysisPromptProvider.class);
    private final MaterialUrlAnalysisPromptBuilder promptBuilder = new MaterialUrlAnalysisPromptBuilder(promptProvider);

    @Test
    void buildsSystemMessageFromResourceProvider() {
        when(promptProvider.systemPrompt()).thenReturn("system prompt");

        assertThat(promptBuilder.buildSystemMessage()).isEqualTo("system prompt");
    }

    @Test
    void systemPromptDoesNotRequestHighlightsAndKeepsUrlAnalysisContract() throws Exception {
        String systemPrompt = Files.readString(
                Path.of("src/main/resources/prompts/material/url-analysis-system-prompt.md"),
                StandardCharsets.UTF_8
        );

        assertThat(systemPrompt)
                .doesNotContain("highlights", "Highlight Extraction", "Highlight Consistency", "Highlight Type")
                .contains("short_summary", "long_analysis", "tags", "recommended_folder");
    }

    @Test
    void buildsUserMessageWithUrlMetadataContentAndFolders() {
        when(promptProvider.userTemplate()).thenReturn(template());
        ExtractedMaterialContent content = new ExtractedMaterialContent(
                "https://example.com/post",
                PlatformType.BLOG,
                "Extracted Title",
                "본문 내용",
                null,
                "author",
                LocalDateTime.of(2026, 7, 25, 10, 0)
        );

        String message = promptBuilder.buildUserMessage(content, List.of("Backend", "Backend", " ", "AI"));

        assertThat(message).contains("https://example.com/post");
        assertThat(message).contains("Extracted Title");
        assertThat(message).contains("BLOG");
        assertThat(message).contains("author");
        assertThat(message).contains("2026-07-25T10:00");
        assertThat(message).contains("- Backend");
        assertThat(message).contains("- AI");
        assertThat(message).contains("본문 내용");
        assertThat(message).doesNotContain("{{ORIGINAL_URL}}", "{{TITLE}}", "{{FOLDER_LIST}}", "{{EXTRACTED_CONTENT}}");
    }

    @Test
    void usesNoInformationForMissingMetadataAndNoFolderMessage() {
        when(promptProvider.userTemplate()).thenReturn("""
                AUTHOR={{AUTHOR}}
                PUBLISHED_AT={{PUBLISHED_AT}}
                FOLDER_LIST={{FOLDER_LIST}}
                PLATFORM_TYPE={{PLATFORM_TYPE}}
                """);
        ExtractedMaterialContent content = new ExtractedMaterialContent(
                "https://example.com/post",
                null,
                " ",
                "content",
                null,
                null,
                null
        );

        String message = promptBuilder.buildUserMessage(content, List.of());
        String noInformation = (String) ReflectionTestUtils.getField(
                MaterialUrlAnalysisPromptBuilder.class,
                "NO_INFORMATION"
        );
        String noFolderMessage = (String) ReflectionTestUtils.getField(
                MaterialUrlAnalysisPromptBuilder.class,
                "NO_FOLDER_MESSAGE"
        );

        assertThat(message).contains("AUTHOR=" + noInformation);
        assertThat(message).contains("PUBLISHED_AT=" + noInformation);
        assertThat(message).contains("FOLDER_LIST=" + noFolderMessage);
        assertThat(message).doesNotContain("{{PLATFORM_TYPE}}", "{{AUTHOR}}", "{{PUBLISHED_AT}}", "{{FOLDER_LIST}}");
    }

    @Test
    void buildsUserMessageWithImageCandidates() {
        when(promptProvider.userTemplate()).thenReturn(template());
        ExtractedMaterialContent content = new ExtractedMaterialContent(
                "https://example.com/post",
                PlatformType.BLOG,
                "Title",
                "content",
                null,
                null,
                null,
                List.of(new MaterialImageCandidate(
                        "https://cdn.example.com/chart.png",
                        "경제 성장률",
                        "연도별 경제 성장률",
                        "차트",
                        "경제 성장 추이",
                        "아래 그래프는 최근 5년간 성장률을 보여준다."
                ))
        );

        String message = promptBuilder.buildUserMessage(content, List.of("경제"));

        assertThat(message).contains("이미지 후보 1");
        assertThat(message).contains("- URL: https://cdn.example.com/chart.png");
        assertThat(message).contains("- ALT: 경제 성장률");
        assertThat(message).contains("- CAPTION: 연도별 경제 성장률");
        assertThat(message).contains("- TITLE: 차트");
        assertThat(message).contains("- SECTION: 경제 성장 추이");
        assertThat(message).contains("- CONTEXT: 아래 그래프는 최근 5년간 성장률을 보여준다.");
        assertThat(message).doesNotContain("{{IMAGE_CANDIDATES}}");
    }

    @Test
    void usesNoImageCandidatesMessageWhenImageCandidatesAreEmpty() {
        when(promptProvider.userTemplate()).thenReturn(template());
        ExtractedMaterialContent content = new ExtractedMaterialContent(
                "https://example.com/post",
                PlatformType.BLOG,
                "Title",
                "content",
                null,
                null,
                null
        );

        String message = promptBuilder.buildUserMessage(content, List.of("Backend"));

        assertThat(message).contains("이미지:\n없음");
    }

    @Test
    void keepsPlaceholderLikeTextInsideExtractedContent() {
        when(promptProvider.userTemplate()).thenReturn(template());
        ExtractedMaterialContent content = new ExtractedMaterialContent(
                "https://example.com/post",
                PlatformType.BLOG,
                "Title",
                "본문에 {{TITLE}} 같은 문자열이 있어도 유지",
                null,
                null,
                null
        );

        String message = promptBuilder.buildUserMessage(content, List.of("Backend"));

        assertThat(message).contains("본문에 {{TITLE}} 같은 문자열이 있어도 유지");
    }

    @Test
    void doesNotInterpretPlaceholderLikeTextInsideReplacementValues() {
        when(promptProvider.userTemplate()).thenReturn(template());
        ExtractedMaterialContent content = new ExtractedMaterialContent(
                "https://example.com/post?$1=\\value",
                PlatformType.BLOG,
                "test {{EXTRACTED_CONTENT}}",
                "본문 {{AUTHOR}} $1 \\ 그대로 유지",
                null,
                "writer {{TITLE}} $1 \\",
                LocalDateTime.of(2026, 7, 25, 10, 0)
        );

        String message = promptBuilder.buildUserMessage(content, List.of("Backend"));

        assertThat(message).contains("https://example.com/post?$1=\\value");
        assertThat(message).contains("test {{EXTRACTED_CONTENT}}");
        assertThat(message).contains("writer {{TITLE}} $1 \\");
        assertThat(message).contains("본문 {{AUTHOR}} $1 \\ 그대로 유지");
    }

    @Test
    void unresolvedTemplatePlaceholderFails() {
        when(promptProvider.userTemplate()).thenReturn(template() + "\n{{UNKNOWN}}");
        ExtractedMaterialContent content = new ExtractedMaterialContent(
                "https://example.com/post",
                PlatformType.BLOG,
                "Title",
                "content",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> promptBuilder.buildUserMessage(content, List.of("Backend")))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
    }

    private String template() {
        return """
                URL:
                {{ORIGINAL_URL}}
                제목:
                {{TITLE}}
                플랫폼:
                {{PLATFORM_TYPE}}
                작성자:
                {{AUTHOR}}
                게시일:
                {{PUBLISHED_AT}}
                폴더:
                {{FOLDER_LIST}}
                본문:
                {{EXTRACTED_CONTENT}}
                이미지:
                {{IMAGE_CANDIDATES}}
                """;
    }
}
