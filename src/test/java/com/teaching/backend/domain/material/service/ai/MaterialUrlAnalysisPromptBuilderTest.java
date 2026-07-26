package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.junit.jupiter.api.Test;

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
        when(promptProvider.userTemplate()).thenReturn(template());
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

        assertThat(message).contains("정보 없음");
        assertThat(message).contains("현재 사용자의 폴더 목록이 없습니다.");
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
                """;
    }
}
