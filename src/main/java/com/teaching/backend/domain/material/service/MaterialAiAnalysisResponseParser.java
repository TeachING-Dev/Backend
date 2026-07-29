package com.teaching.backend.domain.material.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiHighlightResult;
import com.teaching.backend.domain.material.dto.ai.MaterialUrlAnalysisParseResult;
import com.teaching.backend.domain.material.enums.MaterialAiHighlightType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Set<Character> INLINE_MARKDOWN_FORMATTING_CHARS = Set.of('*', '_', '`');
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

        Optional<String> exactText = findExactHighlightText(longAnalysis, highlight.text().trim());
        if (exactText.isEmpty() || !seenTexts.add(exactText.get())) {
            return null;
        }

        MaterialAiHighlightType type;
        try {
            type = MaterialAiHighlightType.fromLabel(highlight.type());
        } catch (MaterialException e) {
            return null;
        }

        return new MaterialAiHighlightResult(exactText.get(), type);
    }

    private Optional<String> findExactHighlightText(String longAnalysis, String highlightText) {
        int exactStart = longAnalysis.indexOf(highlightText);
        if (exactStart >= 0) {
            return Optional.of(longAnalysis.substring(exactStart, exactStart + highlightText.length()));
        }

        NormalizedText normalizedSource = normalizeHighlightSearchText(longAnalysis, true);
        String normalizedTarget = normalizeHighlightSearchText(highlightText, false).value().trim();
        if (normalizedTarget.isBlank()) {
            return Optional.empty();
        }

        int normalizedStart = normalizedSource.value().indexOf(normalizedTarget);
        if (normalizedStart < 0) {
            return Optional.empty();
        }

        int sourceStart = normalizedSource.sourceIndexes().get(normalizedStart);
        int normalizedEnd = normalizedStart + normalizedTarget.length() - 1;
        int sourceEnd = normalizedSource.sourceIndexes().get(normalizedEnd) + 1;

        int[] expandedRange = expandInlineMarkdownBounds(longAnalysis, sourceStart, sourceEnd);
        int trimmedStart = trimLeadingWhitespace(longAnalysis, expandedRange[0], expandedRange[1]);
        int trimmedEnd = trimTrailingWhitespace(longAnalysis, trimmedStart, expandedRange[1]);
        if (trimmedStart >= trimmedEnd) {
            return Optional.empty();
        }

        return Optional.of(longAnalysis.substring(trimmedStart, trimmedEnd));
    }

    private NormalizedText normalizeHighlightSearchText(String value, boolean keepSourceIndexes) {
        StringBuilder normalized = new StringBuilder();
        List<Integer> sourceIndexes = keepSourceIndexes ? new ArrayList<>() : List.of();
        boolean previousWhitespace = false;

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (INLINE_MARKDOWN_FORMATTING_CHARS.contains(current)) {
                continue;
            }

            if (isWhitespaceLike(current)) {
                if (!previousWhitespace) {
                    normalized.append(' ');
                    if (keepSourceIndexes) {
                        sourceIndexes.add(i);
                    }
                    previousWhitespace = true;
                }
                continue;
            }

            normalized.append(current);
            if (keepSourceIndexes) {
                sourceIndexes.add(i);
            }
            previousWhitespace = false;
        }

        return new NormalizedText(normalized.toString(), sourceIndexes);
    }

    private int[] expandInlineMarkdownBounds(String source, int start, int end) {
        int expandedStart = start;
        while (expandedStart > 0 && INLINE_MARKDOWN_FORMATTING_CHARS.contains(source.charAt(expandedStart - 1))) {
            expandedStart--;
        }

        int expandedEnd = end;
        while (expandedEnd < source.length() && INLINE_MARKDOWN_FORMATTING_CHARS.contains(source.charAt(expandedEnd))) {
            expandedEnd++;
        }

        return new int[]{expandedStart, expandedEnd};
    }

    private int trimLeadingWhitespace(String value, int start, int end) {
        int current = start;
        while (current < end && isWhitespaceLike(value.charAt(current))) {
            current++;
        }
        return current;
    }

    private int trimTrailingWhitespace(String value, int start, int end) {
        int current = end;
        while (current > start && isWhitespaceLike(value.charAt(current - 1))) {
            current--;
        }
        return current;
    }

    private boolean isWhitespaceLike(char value) {
        return Character.isWhitespace(value) || value == '\u00A0';
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

    private record NormalizedText(String value, List<Integer> sourceIndexes) {
    }
}
