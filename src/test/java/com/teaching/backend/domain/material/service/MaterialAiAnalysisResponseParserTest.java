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
    void leavesLongAnalysisWithoutImageWhenCandidatesAreEmpty() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of()
        );

        assertThat(result.analysisResult().longAnalysis()).doesNotContain("![");
    }

    @Test
    void insertsOneFallbackMarkdownImageWhenCandidatesExistAndAiUsesNoImage() {
        String imageUrl = "https://cdn.example.com/chart.png";
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(new MaterialImageCandidate(imageUrl, "성장률 그래프", null, null, "Application", null))
        );

        assertThat(result.analysisResult().longAnalysis())
                .contains("![성장률 그래프](%s)".formatted(imageUrl));
        assertThat(markdownImageCount(result.analysisResult().longAnalysis())).isEqualTo(1);
    }

    @Test
    void skipsFallbackCandidateWithWhitespaceUrlAndUsesNextSafeCandidate() {
        String unsafeUrl = "https://cdn.example.com/image space.png";
        String safeUrl = "https://cdn.example.com/safe.png";

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(
                        new MaterialImageCandidate(unsafeUrl, "unsafe", null, null, "Application", "상세 분석 검색 보조 맥락 활용"),
                        new MaterialImageCandidate(safeUrl, "safe", null, null, null, null)
                )
        );

        assertThat(result.analysisResult().longAnalysis())
                .contains("![safe](%s)".formatted(safeUrl))
                .doesNotContain(unsafeUrl);
    }

    @Test
    void skipsFallbackCandidateWithParenthesesUrlAndUsesNextSafeCandidate() {
        String unsafeUrl = "https://cdn.example.com/a(1).png";
        String safeUrl = "https://cdn.example.com/safe.png";

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(
                        new MaterialImageCandidate(unsafeUrl, "unsafe", null, null, "Application", "상세 분석 검색 보조 맥락 활용"),
                        new MaterialImageCandidate(safeUrl, "safe", null, null, null, null)
                )
        );

        assertThat(result.analysisResult().longAnalysis())
                .contains("![safe](%s)".formatted(safeUrl))
                .doesNotContain(unsafeUrl);
    }

    @Test
    void succeedsWithoutFallbackImageWhenAllCandidatesAreMarkdownUnsafe() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(
                        new MaterialImageCandidate("https://cdn.example.com/a(1).png", "first", null, null, "Application", null),
                        new MaterialImageCandidate("https://cdn.example.com/image space.png", "second", null, null, null, null)
                )
        );

        assertThat(result.analysisResult().longAnalysis()).doesNotContain("![");
    }

    @Test
    void ignoresHighScoringUnsafeCandidateAndSelectsLowerScoringSafeCandidate() {
        String unsafeUrl = "https://cdn.example.com/best(1).png";
        String safeUrl = "https://cdn.example.com/safe.png";

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(
                        new MaterialImageCandidate(unsafeUrl, "unsafe", null, null, "Application", "상세 분석 검색 보조 맥락 활용"),
                        new MaterialImageCandidate(safeUrl, "safe", null, null, null, null)
                )
        );

        assertThat(result.analysisResult().longAnalysis())
                .contains("![safe](%s)".formatted(safeUrl))
                .doesNotContain(unsafeUrl);
    }

    @Test
    void keepsAiMarkdownImageUnchangedWhenOneCandidateImageAlreadyExists() {
        String imageUrl = "https://cdn.example.com/chart.png";
        String longAnalysis = validLongAnalysis() + "\n![성장률 그래프](%s)".formatted(imageUrl);

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                urlAnalysisJson("Backend", longAnalysis),
                List.of("Backend"),
                List.of(new MaterialImageCandidate(imageUrl, "fallback alt", null, null, null, null))
        );

        assertThat(result.analysisResult().longAnalysis()).isEqualTo(longAnalysis);
        assertThat(markdownImageCount(result.analysisResult().longAnalysis())).isEqualTo(1);
    }

    @Test
    void keepsAiMarkdownImagesUnchangedWhenMultipleCandidateImagesAlreadyExist() {
        String firstUrl = "https://cdn.example.com/first.png";
        String secondUrl = "https://cdn.example.com/second.png";
        String longAnalysis = validLongAnalysis()
                + "\n![첫 이미지](%s)\n![두 번째 이미지](%s)".formatted(firstUrl, secondUrl);

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                urlAnalysisJson("Backend", longAnalysis),
                List.of("Backend"),
                List.of(
                        new MaterialImageCandidate(firstUrl, "first", null, null, null, null),
                        new MaterialImageCandidate(secondUrl, "second", null, null, null, null)
                )
        );

        assertThat(result.analysisResult().longAnalysis()).isEqualTo(longAnalysis);
        assertThat(markdownImageCount(result.analysisResult().longAnalysis())).isEqualTo(2);
    }

    @Test
    void insertsFallbackImageIntoBestMatchingLaterSection() {
        String imageUrl = "https://cdn.example.com/application.png";

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(new MaterialImageCandidate(
                        imageUrl,
                        "적용 방안 이미지",
                        null,
                        null,
                        "Application",
                        "저장된 상세 분석과 검색 보조 맥락에서 활용되는 흐름"
                ))
        );

        String longAnalysis = result.analysisResult().longAnalysis();
        assertThat(longAnalysis.indexOf("![적용 방안 이미지](%s)".formatted(imageUrl)))
                .isGreaterThan(longAnalysis.indexOf("## Application"))
                .isLessThan(longAnalysis.indexOf("## Conclusion"));
    }

    @Test
    void choosesLaterCandidateWhenItMatchesSectionBetterThanFirstCandidate() {
        String weakUrl = "https://cdn.example.com/weak.png";
        String strongUrl = "https://cdn.example.com/strong.png";

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(
                        new MaterialImageCandidate(weakUrl, "일반 이미지", null, null, null, null),
                        new MaterialImageCandidate(strongUrl, "인덱싱 흐름", null, null, "Application", "상세 분석 검색 보조 맥락 활용")
                )
        );

        assertThat(result.analysisResult().longAnalysis())
                .contains("![인덱싱 흐름](%s)".formatted(strongUrl))
                .doesNotContain(weakUrl);
    }

    @Test
    void prioritizesSectionHeadingMatchOverWeakContextOrAltMatch() {
        String weakUrl = "https://cdn.example.com/weak.png";
        String strongUrl = "https://cdn.example.com/section.png";

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(
                        new MaterialImageCandidate(weakUrl, "Spring", null, null, null, "Spring"),
                        new MaterialImageCandidate(strongUrl, "섹션 이미지", null, null, "Application", null)
                )
        );

        assertThat(result.analysisResult().longAnalysis())
                .contains("![섹션 이미지](%s)".formatted(strongUrl))
                .doesNotContain(weakUrl);
    }

    @Test
    void fallsBackToFirstCandidateAndSafeFirstParagraphWhenNoMetadataMatches() {
        String firstUrl = "https://cdn.example.com/first.png";
        String secondUrl = "https://cdn.example.com/second.png";

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(
                        new MaterialImageCandidate(firstUrl, null, null, null, null, null),
                        new MaterialImageCandidate(secondUrl, "unmatched", null, null, null, null)
                )
        );

        String longAnalysis = result.analysisResult().longAnalysis();
        assertThat(longAnalysis)
                .contains("![이미지](%s)".formatted(firstUrl))
                .doesNotContain(secondUrl);
        assertThat(longAnalysis.indexOf("![이미지](%s)".formatted(firstUrl)))
                .isGreaterThan(longAnalysis.indexOf("## Overview"))
                .isLessThan(longAnalysis.indexOf("## Background"));
    }

    @Test
    void usesCaptionForFallbackDescriptionWhenAltIsBlank() {
        String imageUrl = "https://cdn.example.com/caption.png";

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(new MaterialImageCandidate(imageUrl, " ", "캡션 설명", "제목 설명", null, null))
        );

        assertThat(result.analysisResult().longAnalysis())
                .contains("![캡션 설명](%s)".formatted(imageUrl));
    }

    @Test
    void usesTitleForFallbackDescriptionWhenAltAndCaptionAreBlank() {
        String imageUrl = "https://cdn.example.com/title.png";

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend"),
                List.of("Backend"),
                List.of(new MaterialImageCandidate(imageUrl, null, null, "제목 설명", null, null))
        );

        assertThat(result.analysisResult().longAnalysis())
                .contains("![제목 설명](%s)".formatted(imageUrl));
    }

    @Test
    void doesNotInsertFallbackInsideCodeBlockOrMarkdownTable() {
        String imageUrl = "https://cdn.example.com/safe.png";
        String longAnalysis = """
                ## Overview
                첫 문단은 안전한 삽입 위치입니다.

                ```java
                ## Not a heading
                String value = "code";
                ```

                | A | B |
                |---|---|
                | 1 | 2 |

                ## Background
                * **본문 추출** 단계는 정적 HTML과 렌더링된 HTML을 사용합니다.
                ## Key Points
                * **요약**은 짧게 유지하고 상세 분석은 여러 섹션으로 나눕니다.
                ## Application
                * **저장된 상세 분석**은 자료 상세 화면에서 활용됩니다.
                ## Conclusion
                * 전체 흐름은 원문 정제, AI 분석, 태그 저장, 인덱싱을 분리합니다.
                """;
        longAnalysis = longAnalysis + " 상세한 설명을 충분히 채우기 위해 ".repeat(30);

        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                urlAnalysisJson("Backend", longAnalysis),
                List.of("Backend"),
                List.of(new MaterialImageCandidate(imageUrl, "안전 이미지", null, null, null, null))
        );

        String parsedLongAnalysis = result.analysisResult().longAnalysis();
        assertThat(parsedLongAnalysis).contains("![안전 이미지](%s)".formatted(imageUrl));
        assertThat(parsedLongAnalysis.indexOf("![안전 이미지](%s)".formatted(imageUrl)))
                .isLessThan(parsedLongAnalysis.indexOf("```java"));
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
        return urlAnalysisJson(recommendedFolder, validLongAnalysis() + extraMarkdown);
    }

    private String urlAnalysisJson(String recommendedFolder, String longAnalysis) {
        String folderValue = recommendedFolder == null ? "null" : "\"" + recommendedFolder + "\"";
        return """
                {
                  "short_summary": "summary",
                  "long_analysis": "%s",
                  "tags": ["Spring", "JPA", "Web"],
                  "recommended_folder": %s
                }
                """.formatted(jsonEscape(longAnalysis), folderValue);
    }

    private int markdownImageCount(String value) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf("![", index)) >= 0) {
            count++;
            index += 2;
        }
        return count;
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
