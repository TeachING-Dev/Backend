package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.ai.MaterialUrlAnalysisParseResult;
import com.teaching.backend.domain.material.dto.extract.MaterialImageCandidate;
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
                  "long_analysis": "%s",
                  "highlights": [
                    {"text": "ignored", "type": "MAIN"}
                  ],
                  "tags": ["Spring", "JPA", "Web"],
                  "recommended_folder": "Backend"
                }
                """.formatted(jsonEscape(validLongAnalysis())), List.of("Backend"));

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
        assertUrlAnalysisParseFailed("""
                {
                  "short_summary": "summary",
                  "long_analysis": "%s",
                  "tags": ["Spring", "JPA", "Web"],
                  "recommended_folder": "Backend"
                }
                """.formatted(jsonEscape("plain text ".repeat(80))));
    }

    @Test
    void rejectsTooShortUrlAnalysisLongAnalysis() {
        assertUrlAnalysisParseFailed("""
                {
                  "short_summary": "summary",
                  "long_analysis": "## Overview\\n* **short**",
                  "tags": ["Spring", "JPA", "Web"],
                  "recommended_folder": "Backend"
                }
                """);
    }

    @Test
    void acceptsMarkdownImageFromProvidedCandidate() {
        String imageUrl = "https://cdn.example.com/chart.png";
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend", "![성장률 그래프](%s)".formatted(imageUrl)),
                List.of("Backend"),
                List.of(new MaterialImageCandidate(imageUrl, "성장률", null, null, null, null))
        );

        assertThat(result.analysisResult().longAnalysis()).contains(imageUrl);
    }

    @Test
    void rejectsMarkdownImageWhenUrlWasNotProvided() {
        assertThatThrownBy(() -> parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend", "![임의 이미지](https://other.example.com/fake.png)"),
                List.of("Backend"),
                List.of(new MaterialImageCandidate("https://cdn.example.com/chart.png", null, null, null, null, null))
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
    }

    @Test
    void rejectsMarkdownImageWhenNoCandidatesWereProvided() {
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend", "![임의 이미지](https://other.example.com/fake.png)"));
    }

    @Test
    void acceptsNoMarkdownImageEvenWhenCandidatesExist() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(new MaterialImageCandidate("https://cdn.example.com/chart.png", null, null, null, null, null))
        );

        assertThat(result.analysisResult().longAnalysis()).doesNotContain("![");
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
        return validUrlAnalysisJson(recommendedFolder, "");
    }

    private String validUrlAnalysisJson(String recommendedFolder, String extraMarkdown) {
        String folderValue = recommendedFolder == null ? "null" : "\"" + recommendedFolder + "\"";
        return """
                {
                  "short_summary": "summary",
                  "long_analysis": "%s",
                  "tags": ["Spring", "JPA", "Web"],
                  "recommended_folder": %s
                }
                """.formatted(jsonEscape(validLongAnalysis() + extraMarkdown), folderValue);
    }

    private String validLongAnalysis() {
        return """
                ## Overview
                * **Spring** 기반 URL 분석은 추출된 본문을 구조화해 저장 가능한 요약과 상세 분석으로 변환합니다.
                * 이 분석은 제공된 원문 안에서 핵심 주제, 구현 흐름, 데이터 저장 의미를 분리해 설명합니다.
                ## Background
                * **본문 추출** 단계는 정적 HTML과 필요한 경우 렌더링된 HTML을 사용해 원문을 확보합니다.
                * 이후 AI 단계는 외부 지식이 아니라 전달된 본문과 메타데이터만 사용해야 합니다.
                ## Key Points
                * **요약**은 짧게 유지하고, 상세 분석은 여러 섹션으로 나누어 읽기 쉽게 구성합니다.
                * 태그와 추천 폴더는 사용자가 자료를 다시 찾을 수 있도록 돕는 보조 정보입니다.
                ## Application
                * **저장된 상세 분석**은 자료 상세 화면과 검색 보조 맥락에서 활용될 수 있습니다.
                * 본문 이미지가 있는 경우에는 실제 후보 URL만 사용해 관련 위치에 삽입할 수 있습니다.
                ## Conclusion
                * 전체 흐름은 원문 정제, AI 분석, 태그 저장, 인덱싱을 분리하면서도 동일한 분석 결과를 기준으로 동작합니다.
                """;
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
