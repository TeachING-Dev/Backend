package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiHighlightResult;
import com.teaching.backend.domain.material.dto.ai.MaterialUrlAnalysisParseResult;
import com.teaching.backend.domain.material.enums.MaterialAiHighlightType;
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
    void removesDuplicateAndBlankTags() {
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
    void rejectsMalformedJson() {
        assertParseFailed("{");
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
    void parsesUrlAnalysisJsonWithStrictSchema() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(validUrlAnalysisJson("Backend"), List.of("Backend"));

        assertThat(result.analysisResult().shortSummary()).isEqualTo("핵심 요약");
        assertThat(result.analysisResult().longAnalysis()).contains("## 개요");
        assertThat(result.highlights()).hasSize(3);
        assertThat(result.highlights().get(0).type()).isEqualTo(MaterialAiHighlightType.CORE);
        assertThat(result.analysisResult().tags()).containsExactly("스프링", "JPA", "트랜잭션");
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
    void parsesHighlightTextAsLiteralSubstringOfLongAnalysis() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis("""
                {
                  "short_summary": "summary",
                  "long_analysis": "## Overview\\n- **TCP** keeps reliable delivery. Small packets can be delayed while waiting for ACK. Real-time systems should tune network options carefully.",
                  "highlights": [
                    {"text": "**TCP** keeps reliable delivery.", "type": "핵심"},
                    {"text": "Small packets can be delayed while waiting for ACK.", "type": "주의"},
                    {"text": "Real-time systems should tune network options carefully.", "type": "핵심"}
                  ],
                  "tags": ["TCP", "HTTP", "TLS"],
                  "recommended_folder": null
                }
                """, List.of("Backend"));

        String longAnalysis = result.analysisResult().longAnalysis();
        assertThat(result.highlights())
                .extracting(MaterialAiHighlightResult::text)
                .allSatisfy(highlight -> assertThat(longAnalysis).contains((String) highlight));
    }

    @Test
    void excludesSemanticHighlightThatIsNotLiteralSubstring() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis("""
                {
                  "short_summary": "summary",
                  "long_analysis": "## Overview\\n- **Nagle** reduces frequent transmission of small packets. Small packets can be delayed while waiting for ACK. Real-time systems should tune network options carefully.",
                  "highlights": [
                    {"text": "Nagle can delay small packet transmission.", "type": "핵심"},
                    {"text": "Small packets can be delayed while waiting for ACK.", "type": "주의"},
                    {"text": "Real-time systems should tune network options carefully.", "type": "핵심"}
                  ],
                  "tags": ["TCP", "HTTP", "TLS"],
                  "recommended_folder": null
                }
                """, List.of("Backend"));

        assertThat(result.highlights())
                .extracting(MaterialAiHighlightResult::text)
                .containsExactly(
                        "Small packets can be delayed while waiting for ACK.",
                        "Real-time systems should tune network options carefully."
                );
    }

    @Test
    void normalizesImportantHighlightTypeAliasToCore() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis("""
                {
                  "short_summary": "summary",
                  "long_analysis": "## Overview\\n- **TCP** keeps reliable delivery. Small packets can be delayed while waiting for ACK. Real-time systems should tune network options carefully.",
                  "highlights": [
                    {"text": "**TCP** keeps reliable delivery.", "type": "중요"},
                    {"text": "Small packets can be delayed while waiting for ACK.", "type": "주의"},
                    {"text": "Real-time systems should tune network options carefully.", "type": "핵심"}
                  ],
                  "tags": ["TCP", "HTTP", "TLS"],
                  "recommended_folder": null
                }
                """, List.of("Backend"));

        assertThat(result.highlights().get(0).type()).isEqualTo(MaterialAiHighlightType.CORE);
        assertThat(result.analysisResult().highlights().get(0).type()).isEqualTo("핵심");
    }

    @Test
    void excludesUnrelatedHighlightTypeWhenOtherHighlightsAreValid() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis("""
                {
                  "short_summary": "summary",
                  "long_analysis": "## Overview\\n- **TCP** keeps reliable delivery. Small packets can be delayed while waiting for ACK. Real-time systems should tune network options carefully.",
                  "highlights": [
                    {"text": "**TCP** keeps reliable delivery.", "type": "정보"},
                    {"text": "Small packets can be delayed while waiting for ACK.", "type": "주의"},
                    {"text": "Real-time systems should tune network options carefully.", "type": "핵심"}
                  ],
                  "tags": ["TCP", "HTTP", "TLS"],
                  "recommended_folder": null
                }
                """, List.of("Backend"));

        assertThat(result.highlights())
                .extracting(MaterialAiHighlightResult::text)
                .containsExactly(
                        "Small packets can be delayed while waiting for ACK.",
                        "Real-time systems should tune network options carefully."
                );
    }

    @Test
    void rejectsWhenNoValidHighlightRemains() {
        assertUrlAnalysisParseFailed("""
                {
                  "short_summary": "summary",
                  "long_analysis": "## Overview\\n- **TCP** keeps reliable delivery. Small packets can be delayed while waiting for ACK. Real-time systems should tune network options carefully.",
                  "highlights": [
                    {"text": "missing one", "type": "?듭떖"},
                    {"text": "missing two", "type": "二쇱쓽"},
                    {"text": "missing three", "type": "?듭떖"}
                  ],
                  "tags": ["TCP", "HTTP", "TLS"],
                  "recommended_folder": null
                }
                """);
    }

    @Test
    void parsesUrlAnalysisJsonWithHyphenMarkdownBullets() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis("""
                {
                  "short_summary": "로그인 로직의 핵심을 요약합니다.",
                  "long_analysis": "## 개요\\n- **로그인 로직**을 정리합니다. 로그인 로직은 인증 성공 이후 사용자 세션을 안전하게 유지하는 흐름입니다. 토큰 검증은 요청마다 수행되어야 합니다. 예외 처리는 인증 실패와 권한 부족을 분리해야 합니다.",
                  "highlights": [
                    {"text": "로그인 로직은 인증 성공 이후 사용자 세션을 안전하게 유지하는 흐름입니다.", "type": "핵심"},
                    {"text": "토큰 검증은 요청마다 수행되어야 합니다.", "type": "주의"},
                    {"text": "예외 처리는 인증 실패와 권한 부족을 분리해야 합니다.", "type": "핵심"}
                  ],
                  "tags": ["로그인", "인증", "토큰"],
                  "recommended_folder": "Backend"
                }
                """, List.of("Backend"));

        assertThat(result.analysisResult().shortSummary()).isEqualTo("로그인 로직의 핵심을 요약합니다.");
        assertThat(result.highlights()).hasSize(3);
        assertThat(result.recommendedFolderName()).isEqualTo("Backend");
    }

    @Test
    void parsesUrlAnalysisJsonWithDifferentHeadingLevelAndNumberedList() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis("""
                {
                  "short_summary": "로그인 흐름을 요약합니다.",
                  "long_analysis": "### 개요\\n1. 로그인 흐름은 인증 요청과 응답을 분리해 처리하는 구조입니다. 세션 유지 전략은 서비스 요구사항에 맞춰 선택해야 합니다. 예외 응답은 사용자에게 필요한 수준으로 제한해야 합니다.",
                  "highlights": [
                    {"text": "로그인 흐름은 인증 요청과 응답을 분리해 처리하는 구조입니다.", "type": "핵심"},
                    {"text": "세션 유지 전략은 서비스 요구사항에 맞춰 선택해야 합니다.", "type": "핵심"},
                    {"text": "예외 응답은 사용자에게 필요한 수준으로 제한해야 합니다.", "type": "주의"}
                  ],
                  "tags": ["로그인", "세션", "예외"],
                  "recommended_folder": null
                }
                """, List.of("Backend"));

        assertThat(result.analysisResult().longAnalysis()).contains("### 개요");
        assertThat(result.highlights()).hasSize(3);
        assertThat(result.recommendedFolderName()).isNull();
    }

    @Test
    void parsesUrlAnalysisJsonWithInlineHyphenList() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis("""
                {
                  "short_summary": "로그인 분석 요약입니다.",
                  "long_analysis": "개요 - 로그인 처리는 인증 성공 후 사용자 정보를 안전하게 관리하는 과정입니다. 토큰 저장 위치는 보안 요구사항에 따라 결정해야 합니다. 실패 응답은 상세 내부 정보를 노출하지 않아야 합니다.",
                  "highlights": [
                    {"text": "로그인 처리는 인증 성공 후 사용자 정보를 안전하게 관리하는 과정입니다.", "type": "핵심"},
                    {"text": "토큰 저장 위치는 보안 요구사항에 따라 결정해야 합니다.", "type": "주의"},
                    {"text": "실패 응답은 상세 내부 정보를 노출하지 않아야 합니다.", "type": "주의"}
                  ],
                  "tags": ["로그인", "토큰", "보안"],
                  "recommended_folder": null
                }
                """, List.of());

        assertThat(result.analysisResult().longAnalysis()).contains("개요 - 로그인");
        assertThat(result.highlights()).hasSize(3);
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
    void malformedUrlAnalysisJsonPreservesCause() {
        assertThatThrownBy(() -> parser.parseUrlAnalysis("{", List.of("Backend")))
                .isInstanceOf(MaterialException.class)
                .hasCauseInstanceOf(Exception.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
    }

    @Test
    void unknownRecommendedFolderIsNormalizedToNull() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(validUrlAnalysisJson("Unknown"), List.of("Backend"));

        assertThat(result.recommendedFolderName()).isNull();
        assertThat(result.analysisResult().recommendedFolder()).isNull();
    }

    @Test
    void excludesInvalidHighlightTypeInUrlAnalysisWhenOtherHighlightsAreValid() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(
                validUrlAnalysisJson("Backend").replaceFirst("\"핵심\"", "\"정보\""),
                List.of("Backend")
        );

        assertThat(result.highlights()).hasSize(2);
        assertThat(result.highlights())
                .extracting(MaterialAiHighlightResult::text)
                .doesNotContain("스프링 트랜잭션은 데이터 정합성을 지키는 핵심 장치입니다.");
    }

    @Test
    void excludesHighlightNotContainedInLongAnalysisWhenOtherHighlightsAreValid() {
        MaterialUrlAnalysisParseResult result = parser.parseUrlAnalysis(validUrlAnalysisJson("Backend").replace(
                "\"스프링 트랜잭션은 데이터 정합성을 지키는 핵심 장치입니다.\"",
                "\"본문에 없는 문장입니다.\""
        ), List.of("Backend"));

        assertThat(result.highlights()).hasSize(2);
        assertThat(result.highlights())
                .extracting(MaterialAiHighlightResult::text)
                .doesNotContain("본문에 없는 문장입니다.");
    }

    @Test
    void rejectsTooFewHighlights() {
        assertUrlAnalysisParseFailed("""
                {
                  "short_summary": "핵심 요약",
                  "long_analysis": "%s",
                  "highlights": [
                    {"text": "스프링 트랜잭션은 데이터 정합성을 지키는 핵심 장치입니다.", "type": "핵심"},
                    {"text": "전파 옵션은 호출 흐름에 따라 커밋 경계를 바꿀 수 있습니다.", "type": "주의"}
                  ],
                  "tags": ["스프링", "JPA", "트랜잭션"],
                  "recommended_folder": null
                }
                """.formatted(escapedLongAnalysis()));
    }

    @Test
    void rejectsTooManyHighlights() {
        assertUrlAnalysisParseFailed("""
                {
                  "short_summary": "핵심 요약",
                  "long_analysis": "%s",
                  "highlights": [
                    {"text": "스프링 트랜잭션은 데이터 정합성을 지키는 핵심 장치입니다.", "type": "핵심"},
                    {"text": "전파 옵션은 호출 흐름에 따라 커밋 경계를 바꿀 수 있습니다.", "type": "주의"},
                    {"text": "롤백 정책은 예외 타입에 따라 다르게 동작할 수 있습니다.", "type": "주의"},
                    {"text": "데이터 정합성은 트랜잭션 경계 안에서 유지되어야 합니다.", "type": "핵심"},
                    {"text": "외부 저장소는 별도 보상 정책이 필요할 수 있습니다.", "type": "주의"},
                    {"text": "분석 결과는 검증된 뒤 저장되어야 합니다.", "type": "핵심"}
                  ],
                  "tags": ["스프링", "JPA", "트랜잭션"],
                  "recommended_folder": null
                }
                """.formatted(
                "## 개요\\n* **정합성**을 지키려면 트랜잭션 경계를 이해해야 합니다. "
                        + "스프링 트랜잭션은 데이터 정합성을 지키는 핵심 장치입니다. "
                        + "전파 옵션은 호출 흐름에 따라 커밋 경계를 바꿀 수 있습니다. "
                        + "롤백 정책은 예외 타입에 따라 다르게 동작할 수 있습니다. "
                        + "데이터 정합성은 트랜잭션 경계 안에서 유지되어야 합니다. "
                        + "외부 저장소는 별도 보상 정책이 필요할 수 있습니다. "
                        + "분석 결과는 검증된 뒤 저장되어야 합니다."
        ));
    }

    @Test
    void rejectsInvalidTagsInUrlAnalysis() {
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend").replace(
                "\"스프링\", \"JPA\", \"트랜잭션\"",
                "\"스프링\", \"스프링\", \"트랜잭션\""
        ));
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend").replace(
                "\"스프링\", \"JPA\", \"트랜잭션\"",
                "\"스프링\", \"\", \"트랜잭션\""
        ));
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend").replace(
                "\"스프링\", \"JPA\", \"트랜잭션\"",
                "\"스프링\", \"JPA\", \"열한글자태그입니다추가\""
        ));
    }

    @Test
    void rejectsMarkdownSyntaxMissingInLongAnalysis() {
        assertUrlAnalysisParseFailed(validUrlAnalysisJson("Backend")
                .replace("## 개요", "개요")
                .replace("* **정합성**을", "정합성을"));
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
                  "short_summary": "핵심 요약",
                  "long_analysis": "%s",
                  "highlights": [
                    {"text": "스프링 트랜잭션은 데이터 정합성을 지키는 핵심 장치입니다.", "type": "핵심"},
                    {"text": "전파 옵션은 호출 흐름에 따라 커밋 경계를 바꿀 수 있습니다.", "type": "주의"},
                    {"text": "롤백 정책은 예외 타입에 따라 다르게 동작할 수 있습니다.", "type": "주의"}
                  ],
                  "tags": ["스프링", "JPA", "트랜잭션"],
                  "recommended_folder": %s
                }
                """.formatted(escapedLongAnalysis(), folderValue);
    }

    private String escapedLongAnalysis() {
        return "## 개요\\n* **정합성**을 지키려면 트랜잭션 경계를 이해해야 합니다. "
                + "스프링 트랜잭션은 데이터 정합성을 지키는 핵심 장치입니다. "
                + "전파 옵션은 호출 흐름에 따라 커밋 경계를 바꿀 수 있습니다. "
                + "롤백 정책은 예외 타입에 따라 다르게 동작할 수 있습니다.";
    }
}
