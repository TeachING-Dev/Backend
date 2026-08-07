package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.ai.MaterialUrlAnalysisParseResult;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialAiAnalysisResponseParserTest {

    private final MaterialAiAnalysisResponseParser parser = new MaterialAiAnalysisResponseParser();

    @Test
    void parsesNormalJson() {
        MaterialAiAnalysisResult result = parser.parse("""
                {
                  "short_summary": "summary",
                  "long_analysis": "detail",
                  "tags": [" spring ", "jpa"]
                }
                """);

        assertThat(result.shortSummary()).isEqualTo("summary");
        assertThat(result.longAnalysis()).isEqualTo("detail");
        assertThat(result.tags()).containsExactly("spring", "jpa");
    }

    @Test
    void parsesJsonInsideMarkdownCodeFence() {
        MaterialAiAnalysisResult result = parser.parse("""
                ```json
                {
                  "short_summary": "summary",
                  "long_analysis": "detail",
                  "tags": ["spring"]
                }
                ```
                """);

        assertThat(result.shortSummary()).isEqualTo("summary");
    }

    @Test
    void removesDuplicateAndBlankTagsInLegacyParse() {
        MaterialAiAnalysisResult result = parser.parse("""
                {
                  "short_summary": "summary",
                  "long_analysis": "detail",
                  "tags": ["spring", " spring ", "", null, "jpa"]
                }
                """);

        assertThat(result.tags()).containsExactly("spring", "jpa");
    }

    @Test
    void ignoresUnknownFieldsWithoutMapAbuse() {
        MaterialAiAnalysisResult result = parser.parse("""
                {
                  "short_summary": "summary",
                  "long_analysis": "detail",
                  "unknown": "ignored"
                }
                """);

        assertThat(result.shortSummary()).isEqualTo("summary");
    }

    @Test
    void parsesUrlAnalysisJsonWithFourFieldContract() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(validUrlAnalysisJson("Backend"), List.of("Backend"));

        assertThat(result.analysisResult().shortSummary()).isEqualTo("summary");
        assertThat(result.analysisResult().longAnalysis()).contains("## Overview");
        assertThat(result.analysisResult().tags()).containsExactly("Spring", "JPA", "Web");
        assertThat(result.recommendedFolderName()).isEqualTo("Backend");
    }

    @Test
    void parsesUrlAnalysisJsonWhenHighlightsAreReturnedAsUnknownField() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis("""
                {
                  "short_summary": "summary",
                  "long_analysis": "## Overview\\n* **Spring** keeps the code structured and testable.",
                  "highlights": [
                    {"text": "ignored", "type": "MAIN"}
                  ],
                  "tags": ["Spring", "JPA", "Web"],
                  "recommended_folder": "Backend"
                }
                """, List.of("Backend"));

        assertThat(result.analysisResult().shortSummary()).isEqualTo("summary");
        assertThat(result.recommendedFolderName()).isEqualTo("Backend");
    }

    @Test
    void parsesUrlAnalysisJsonInsidePlainMarkdownFence() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis("""
                ```
                %s
                ```
                """.formatted(validUrlAnalysisJson(null)), List.of("Backend"));

        assertThat(result.recommendedFolderName()).isNull();
    }

    @Test
    void stringNullRecommendedFolderIsNormalizedToNull() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("null"),
                List.of("Backend", "null")
        );

        assertThat(result.recommendedFolderName()).isNull();
        assertThat(result.analysisResult().recommendedFolder()).isNull();
    }

    @Test
    void unknownRecommendedFolderIsNormalizedToNull() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(validUrlAnalysisJson("Unknown"), List.of("Backend"));

        assertThat(result.recommendedFolderName()).isNull();
        assertThat(result.analysisResult().recommendedFolder()).isNull();
    }

    @Test
    void rejectsMalformedJson() {
        assertParseFailed("{");
    }

    @Test
    void malformedUrlAnalysisJsonPreservesCause() {
        assertThatThrownBy(() -> parser.parseUrlAnalysis("{", List.of("Backend")))
                .isInstanceOf(MaterialException.class)
                .hasCauseInstanceOf(Exception.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
    }

    @Test
    void rejectsMissingRequiredSummary() {
        assertParseFailed("""
                {"long_analysis": "detail", "tags": []}
                """);
    }

    @Test
    void rejectsBlankRequiredDetail() {
        assertParseFailed("""
                {"short_summary": "summary", "long_analysis": "   ", "tags": []}
                """);
    }

    @Test
    void rejectsBlankUrlAnalysisSummary() {
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend").replace("\"summary\"", "\"  \""));
    }

    @Test
    void rejectsBlankUrlAnalysisLongAnalysis() {
        assertUrlAnalysisParseFailed("""
                {
                  "short_summary": "summary",
                  "long_analysis": "  ",
                  "tags": ["Spring", "JPA", "Web"],
                  "recommended_folder": "Backend"
                }
                """);
    }

    @Test
    void rejectsMarkdownSyntaxMissingInLongAnalysis() {
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend")
                .replace("## Overview", "Overview")
                .replace("* **Spring**", "Spring"));
    }

    @Test
    void rejectsInvalidTagsInUrlAnalysis() {
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend").replace(
                "\"Spring\", \"JPA\", \"Web\"",
                "\"Spring\", \"Spring\", \"Web\""
        ));
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend").replace(
                "\"Spring\", \"JPA\", \"Web\"",
                "\"Spring\", \"\", \"Web\""
        ));
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend").replace(
                "\"Spring\", \"JPA\", \"Web\"",
                "\"Spring\", \"JPA\", \"VeryLongTagName\""
        ));
    }

    @Test
    void rejectsTooFewTags() {
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend").replace(
                "\"Spring\", \"JPA\", \"Web\"",
                "\"Spring\", \"JPA\""
        ));
    }

    @Test
    void rejectsTooManyTags() {
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend").replace(
                "\"Spring\", \"JPA\", \"Web\"",
                "\"Spring\", \"JPA\", \"Web\", \"AI\", \"DB\", \"HTTP\""
        ));
    }

    private void assertParseFailed(String rawContent) {
        assertThatThrownBy(() -> parser.parse(rawContent))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
    }

    private void assertUrlAnalysisParseFailed(String rawContent) {
        assertThatThrownBy(() -> parser.parseUrlAnalysis(rawContent, List.of("Backend")))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
    }

    private String validUrlAnalysisJson(String recommendedFolder) {
        String folderValue = recommendedFolder == null ? "null" : "\"" + recommendedFolder + "\"";
        return """
                {
                  "short_summary": "summary",
                  "long_analysis": "## Overview\\n* **Spring** keeps the code structured and testable.",
                  "tags": ["Spring", "JPA", "Web"],
                  "recommended_folder": %s
                }
                """.formatted(folderValue);
    }
}
