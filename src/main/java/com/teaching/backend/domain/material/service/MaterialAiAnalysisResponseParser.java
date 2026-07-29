package com.teaching.backend.domain.material.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaching.backend.domain.material.dto.ai.MaterialAiHighlightResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.ai.MaterialUrlAnalysisParseResult;
import com.teaching.backend.domain.material.enums.MaterialAiHighlightType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// OpenAI 응답 문자열을 MaterialAiAnalysisResult로 파싱/검증하는 컴포넌트.
// response_format=json_object로 요청하지만, 모델이 코드펜스를 덧붙이는 경우까지 방어적으로 처리한다.
// Spring Boot 4는 기본적으로 Jackson 3(tools.jackson.databind.ObjectMapper) 빈만 자동 구성하므로,
// 이 클래스 전용으로 com.fasterxml.jackson(Jackson 2) ObjectMapper를 직접 소유한다.
@Component
@Slf4j
public class MaterialAiAnalysisResponseParser {

    private static final Pattern CODE_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
    private static final Pattern MARKDOWN_HEADING_PATTERN =
            Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s*\\S+");
    private static final Pattern MARKDOWN_BULLET_PATTERN =
            Pattern.compile("(?m)(^|\\R)\\s*([-*]|\\d+[.)])\\s+");
    private static final Pattern MARKDOWN_INLINE_LIST_PATTERN =
            Pattern.compile("\\s[-*]\\s+");
    private static final int MIN_LONG_ANALYSIS_LENGTH = 20;
    private static final int RAW_RESPONSE_LOG_LIMIT = 500;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MaterialAiAnalysisResult parse(String rawContent) {
        MaterialAiAnalysisResult result = readResult(rawContent);

        if (result.shortSummary() == null || result.shortSummary().isBlank()
                || result.longAnalysis() == null || result.longAnalysis().isBlank()) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
        }

        List<String> tags = normalizeTags(result.tags());

        return new MaterialAiAnalysisResult(
                result.shortSummary().trim(),
                result.longAnalysis().trim(),
                tags,
                result.highlights(),
                result.recommendedFolder()
        );
    }

    public MaterialUrlAnalysisParseResult parseUrlAnalysis(
            String rawContent,
            List<String> folderNames
    ) {
        MaterialAiAnalysisResult result = readResult(rawContent);
        String shortSummary = requiredTrimmed(result.shortSummary());
        String longAnalysis = requiredTrimmed(result.longAnalysis());
        validateMarkdownAnalysis(longAnalysis);

        List<MaterialAiHighlightResult> highlights = validateHighlights(result.highlights(), longAnalysis);
        List<String> tags = validateTags(result.tags());
        String recommendedFolderName = normalizeRecommendedFolder(result.recommendedFolder(), folderNames);

        MaterialAiAnalysisResult normalized = new MaterialAiAnalysisResult(
                shortSummary,
                longAnalysis,
                tags,
                highlights.stream()
                        .map(highlight -> new MaterialAiAnalysisResult.Highlight(
                                highlight.text(),
                                highlight.type().getLabel()
                        ))
                        .toList(),
                recommendedFolderName
        );

        return new MaterialUrlAnalysisParseResult(normalized, highlights, recommendedFolderName);
    }

    private MaterialAiAnalysisResult readResult(String rawContent) {
        String jsonText = stripCodeFence(rawContent);

        try {
            return objectMapper.readValue(jsonText, MaterialAiAnalysisResult.class);
        } catch (Exception e) {
            logParseFailure("json_read_failed", rawContent, e);
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED, e);
        }
    }

    private String requiredTrimmed(String value) {
        if (value == null || value.isBlank()) {
            throw parseFailed("required_value_blank");
        }
        return value.trim();
    }

    private void validateMarkdownAnalysis(String longAnalysis) {
        boolean hasMarkdownSignal = MARKDOWN_HEADING_PATTERN.matcher(longAnalysis).find()
                || MARKDOWN_BULLET_PATTERN.matcher(longAnalysis).find()
                || MARKDOWN_INLINE_LIST_PATTERN.matcher(longAnalysis).find()
                || longAnalysis.contains("**");
        if (longAnalysis.length() < MIN_LONG_ANALYSIS_LENGTH || !hasMarkdownSignal) {
            throw parseFailed("long_analysis_markdown_syntax_invalid");
        }
    }

    private List<MaterialAiHighlightResult> validateHighlights(
            List<MaterialAiAnalysisResult.Highlight> highlights,
            String longAnalysis
    ) {
        if (highlights == null || highlights.size() < 3 || highlights.size() > 5) {
            throw parseFailed("highlights_count_invalid");
        }

        Set<String> seenTexts = new LinkedHashSet<>();
        List<MaterialAiHighlightResult> validHighlights = highlights.stream()
                .map(highlight -> normalizeHighlight(highlight, longAnalysis, seenTexts))
                .filter(highlight -> highlight != null)
                .toList();

        if (validHighlights.isEmpty()) {
            throw parseFailed("highlight_text_invalid");
        }
        if (validHighlights.size() < highlights.size()) {
            log.warn(
                    "AI analysis response contained invalid highlights. requestedCount={}, validCount={}",
                    highlights.size(),
                    validHighlights.size()
            );
        }
        return validHighlights;
    }

    private MaterialAiHighlightResult normalizeHighlight(
            MaterialAiAnalysisResult.Highlight highlight,
            String longAnalysis,
            Set<String> seenTexts
    ) {
        if (highlight == null || highlight.text() == null || highlight.text().isBlank()) {
            return null;
        }

        String text = highlight.text().trim();
        int highlightStart = longAnalysis.indexOf(text);

        if (highlightStart < 0) {
            String cleanTarget = text.replaceAll("[^a-zA-Z0-9가-힣]", "");
            String cleanSource = longAnalysis.replaceAll("[^a-zA-Z0-9가-힣]", "");

            if (!cleanTarget.isBlank() && cleanSource.contains(cleanTarget)) {
                highlightStart = 0;
            }
        }

        if (!seenTexts.add(text) || highlightStart < 0) {
            return null;
        }

        MaterialAiHighlightType type;
        try {
            type = MaterialAiHighlightType.fromLabel(highlight.type());
        } catch (MaterialException e) {
            return null;
        }

        return new MaterialAiHighlightResult(text, type);
    }

    private List<String> validateTags(List<String> tags) {
        if (tags == null || tags.size() < 3 || tags.size() > 5) {
            throw parseFailed("tags_count_invalid");
        }

        Set<String> seenTags = new LinkedHashSet<>();
        for (String tag : tags) {
            String normalized = requiredTrimmed(tag);
            if (normalized.length() > 10 || !seenTags.add(normalized)) {
                throw parseFailed("tag_value_invalid");
            }
        }
        return List.copyOf(seenTags);
    }

    private String normalizeRecommendedFolder(String recommendedFolder, List<String> folderNames) {
        if (recommendedFolder == null || recommendedFolder.isBlank()) {
            return null;
        }

        String normalized = recommendedFolder.trim();
        if ("null".equalsIgnoreCase(normalized)) {
            return null;
        }

        Set<String> availableFolderNames = new LinkedHashSet<>();
        if (folderNames != null) {
            folderNames.stream()
                    .map(name -> name == null ? "" : name.trim())
                    .filter(name -> !name.isBlank())
                    .forEach(availableFolderNames::add);
        }

        return availableFolderNames.contains(normalized) ? normalized : null;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        return new LinkedHashSet<>(tags.stream()
                .map(tag -> tag == null ? "" : tag.trim())
                .filter(tag -> !tag.isBlank())
                .toList())
                .stream()
                .toList();
    }

    private String stripCodeFence(String rawContent) {
        if (rawContent == null) {
            return null;
        }

        Matcher matcher = CODE_FENCE_PATTERN.matcher(rawContent.strip());
        return matcher.matches() ? matcher.group(1) : rawContent.strip();
    }

    private MaterialException parseFailed(String reason) {
        log.warn("AI analysis response parse validation failed. reason={}", reason);
        return new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
    }

    private void logParseFailure(
            String reason,
            String rawContent,
            Exception exception
    ) {
        log.warn(
                "AI analysis response JSON parse failed. reason={}, rawLength={}, rawPrefix={}, exception={}, message={}",
                reason,
                rawContent == null ? null : rawContent.length(),
                truncate(rawContent),
                exception.getClass().getName(),
                exception.getMessage()
        );
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.replaceAll("[\\r\\n\\t ]+", " ").trim();
        if (normalized.length() <= RAW_RESPONSE_LOG_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, RAW_RESPONSE_LOG_LIMIT) + "...";
    }
}
