package com.teaching.backend.domain.material.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.ai.MaterialUrlAnalysisParseResult;
import com.teaching.backend.domain.material.dto.extract.MaterialImageCandidate;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final Pattern MARKDOWN_SECTION_HEADING_PATTERN =
            Pattern.compile("(?m)^\\s{0,3}##\\s+\\S+");
    private static final Pattern SECTION_HEADING_LINE_PATTERN =
            Pattern.compile("^\\s{0,3}##\\s+(.+?)\\s*$");
    private static final Pattern MARKDOWN_IMAGE_PATTERN =
            Pattern.compile("!\\[[^\\]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final int MIN_LONG_ANALYSIS_LENGTH = 500;
    private static final int FALLBACK_IMAGE_DESCRIPTION_LIMIT = 80;
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
                result.recommendedFolder()
        );
    }

    public MaterialUrlAnalysisParseResult parseUrlAnalysis(
            String rawContent,
            List<String> folderNames
    ) {
        return parseUrlAnalysis(rawContent, folderNames, List.of());
    }

    public MaterialUrlAnalysisParseResult parseUrlAnalysis(
            String rawContent,
            List<String> folderNames,
            List<MaterialImageCandidate> imageCandidates
    ) {
        MaterialAiAnalysisResult result = readResult(rawContent);
        String shortSummary = requiredTrimmed(result.shortSummary());
        String longAnalysis = requiredTrimmed(result.longAnalysis());
        validateMarkdownAnalysis(longAnalysis);
        validateMarkdownImageUrls(longAnalysis, imageCandidates);
        longAnalysis = ensureMinimumMarkdownImage(longAnalysis, imageCandidates);
        validateMarkdownImageUrls(longAnalysis, imageCandidates);

        List<String> tags = validateTags(result.tags());
        String recommendedFolderName = normalizeRecommendedFolder(result.recommendedFolder(), folderNames);

        MaterialAiAnalysisResult normalized = new MaterialAiAnalysisResult(
                shortSummary,
                longAnalysis,
                tags,
                recommendedFolderName
        );

        return new MaterialUrlAnalysisParseResult(normalized, recommendedFolderName);
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

    private String ensureMinimumMarkdownImage(
            String longAnalysis,
            List<MaterialImageCandidate> imageCandidates
    ) {
        List<MaterialImageCandidate> validCandidates = validImageCandidates(imageCandidates);
        if (validCandidates.isEmpty() || hasMarkdownImage(longAnalysis)) {
            return longAnalysis;
        }

        FallbackImagePlacement placement = selectFallbackImagePlacement(longAnalysis, validCandidates);
        String markdownImage = fallbackMarkdownImage(placement.candidate());
        return insertMarkdownImage(longAnalysis, placement.section(), markdownImage);
    }

    private List<MaterialImageCandidate> validImageCandidates(List<MaterialImageCandidate> imageCandidates) {
        if (imageCandidates == null || imageCandidates.isEmpty()) {
            return List.of();
        }
        return imageCandidates.stream()
                .filter(candidate -> candidate != null
                        && candidate.url() != null
                        && isMarkdownSafeImageUrl(candidate.url()))
                .toList();
    }

    private boolean isMarkdownSafeImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String normalized = url.trim();
        return normalized.indexOf('(') < 0
                && normalized.indexOf(')') < 0
                && normalized.chars().noneMatch(Character::isWhitespace);
    }

    private boolean hasMarkdownImage(String longAnalysis) {
        return MARKDOWN_IMAGE_PATTERN.matcher(longAnalysis).find();
    }

    private FallbackImagePlacement selectFallbackImagePlacement(
            String longAnalysis,
            List<MaterialImageCandidate> imageCandidates
    ) {
        List<MarkdownSection> sections = parseMarkdownSections(longAnalysis);
        FallbackImagePlacement best = null;

        for (int candidateIndex = 0; candidateIndex < imageCandidates.size(); candidateIndex++) {
            MaterialImageCandidate candidate = imageCandidates.get(candidateIndex);
            for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                MarkdownSection section = sections.get(sectionIndex);
                int score = scoreCandidateForSection(candidate, section);
                FallbackImagePlacement current = new FallbackImagePlacement(
                        candidate,
                        section,
                        score,
                        candidateIndex,
                        sectionIndex
                );
                if (isBetterPlacement(current, best)) {
                    best = current;
                }
            }
        }

        if (best == null || best.score() <= 0) {
            return new FallbackImagePlacement(
                    imageCandidates.get(0),
                    sections.get(0),
                    0,
                    0,
                    0
            );
        }
        return best;
    }

    private boolean isBetterPlacement(FallbackImagePlacement current, FallbackImagePlacement best) {
        if (best == null) {
            return true;
        }
        if (current.score() != best.score()) {
            return current.score() > best.score();
        }
        if (current.candidateIndex() != best.candidateIndex()) {
            return current.candidateIndex() < best.candidateIndex();
        }
        return current.sectionIndex() < best.sectionIndex();
    }

    private int scoreCandidateForSection(MaterialImageCandidate candidate, MarkdownSection section) {
        int score = 0;
        score += scoreField(candidate.sectionHeading(), section, 12);
        score += scoreField(candidate.context(), section, 7);
        score += scoreField(candidate.caption(), section, 5);
        score += scoreField(candidate.alt(), section, 3);
        score += scoreField(candidate.title(), section, 2);
        return score;
    }

    private int scoreField(String value, MarkdownSection section, int weight) {
        String normalizedValue = normalizeSemanticText(value);
        if (normalizedValue.isBlank()) {
            return 0;
        }

        String normalizedHeading = normalizeSemanticText(section.heading());
        String normalizedBody = normalizeSemanticText(section.body());
        int score = 0;

        if (containsMeaningfulPhrase(normalizedHeading, normalizedValue)) {
            score += weight * 12;
        }
        if (containsMeaningfulPhrase(normalizedBody, normalizedValue)) {
            score += weight * 6;
        }

        Set<String> valueTokens = semanticTokens(normalizedValue);
        if (!valueTokens.isEmpty()) {
            score += commonTokenCount(valueTokens, semanticTokens(normalizedHeading)) * weight * 4;
            score += commonTokenCount(valueTokens, semanticTokens(normalizedBody)) * weight;
        }

        return score;
    }

    private boolean containsMeaningfulPhrase(String left, String right) {
        return left.length() >= 4
                && right.length() >= 4
                && (left.contains(right) || right.contains(left));
    }

    private int commonTokenCount(Set<String> left, Set<String> right) {
        int count = 0;
        for (String token : left) {
            if (right.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private Set<String> semanticTokens(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : value.split("\\s+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String normalizeSemanticText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[`*_#>!\\[\\](){}|.,:;\"'“”‘’]", " ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<MarkdownSection> parseMarkdownSections(String longAnalysis) {
        List<MarkdownSection> sections = new ArrayList<>();
        String[] lines = longAnalysis.split("(?<=\\n)", -1);
        boolean fencedCode = false;
        PendingSection current = null;
        int offset = 0;

        for (String line : lines) {
            String lineWithoutNewline = stripTrailingNewline(line);
            String trimmed = lineWithoutNewline.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                fencedCode = !fencedCode;
            }

            Matcher headingMatcher = SECTION_HEADING_LINE_PATTERN.matcher(lineWithoutNewline);
            if (!fencedCode && headingMatcher.matches()) {
                if (current != null) {
                    sections.add(current.toSection(offset, longAnalysis));
                }
                current = new PendingSection(
                        sections.size(),
                        cleanHeadingText(headingMatcher.group(1)),
                        offset,
                        offset + line.length()
                );
            }
            offset += line.length();
        }

        if (current != null) {
            sections.add(current.toSection(longAnalysis.length(), longAnalysis));
        }
        if (sections.isEmpty()) {
            sections.add(new MarkdownSection(0, "", longAnalysis, 0, 0, longAnalysis.length()));
        }
        return sections;
    }

    private String stripTrailingNewline(String value) {
        return value.replaceFirst("\\r?\\n$", "");
    }

    private String cleanHeadingText(String heading) {
        return heading == null ? "" : heading.trim();
    }

    private String fallbackMarkdownImage(MaterialImageCandidate candidate) {
        String description = firstNonBlank(candidate.alt(), candidate.caption(), candidate.title(), "이미지");
        description = description.replaceAll("[\\r\\n\\t ]+", " ").trim();
        if (description.length() > FALLBACK_IMAGE_DESCRIPTION_LIMIT) {
            description = description.substring(0, FALLBACK_IMAGE_DESCRIPTION_LIMIT).trim();
        }
        description = description.replace("[", "").replace("]", "");
        return "![%s](%s)".formatted(description, candidate.url().trim());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "이미지";
    }

    private String insertMarkdownImage(
            String longAnalysis,
            MarkdownSection section,
            String markdownImage
    ) {
        int insertOffset = findInsertionOffset(longAnalysis, section);
        String prefix = longAnalysis.substring(0, insertOffset).stripTrailing();
        String suffix = longAnalysis.substring(insertOffset).stripLeading();
        if (suffix.isBlank()) {
            return prefix + System.lineSeparator() + System.lineSeparator() + markdownImage;
        }
        return prefix
                + System.lineSeparator()
                + System.lineSeparator()
                + markdownImage
                + System.lineSeparator()
                + System.lineSeparator()
                + suffix;
    }

    private int findInsertionOffset(String longAnalysis, MarkdownSection section) {
        String sectionText = longAnalysis.substring(section.contentStart(), section.end());
        String[] lines = sectionText.split("(?<=\\n)", -1);
        boolean fencedCode = false;
        int offset = section.contentStart();
        int paragraphStart = -1;
        int paragraphEnd = -1;

        for (String line : lines) {
            String lineWithoutNewline = stripTrailingNewline(line);
            String trimmed = lineWithoutNewline.trim();
            boolean fenceLine = trimmed.startsWith("```") || trimmed.startsWith("~~~");
            if (fenceLine) {
                if (paragraphStart >= 0) {
                    return paragraphEnd;
                }
                fencedCode = !fencedCode;
                offset += line.length();
                continue;
            }

            if (fencedCode || trimmed.isBlank() || isUnsafeInsertionLine(trimmed)) {
                if (paragraphStart >= 0) {
                    return paragraphEnd;
                }
                offset += line.length();
                continue;
            }

            if (paragraphStart < 0) {
                paragraphStart = offset;
            }
            paragraphEnd = offset + line.length();
            offset += line.length();
        }

        if (paragraphStart >= 0) {
            return paragraphEnd;
        }
        return section.end();
    }

    private boolean isUnsafeInsertionLine(String trimmedLine) {
        return trimmedLine.startsWith("|")
                || trimmedLine.startsWith("## ")
                || trimmedLine.startsWith("![");
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
        if (longAnalysis.length() < MIN_LONG_ANALYSIS_LENGTH
                || !hasMarkdownSignal
                || !MARKDOWN_SECTION_HEADING_PATTERN.matcher(longAnalysis).find()) {
            throw parseFailed("long_analysis_markdown_syntax_invalid");
        }
    }

    private void validateMarkdownImageUrls(
            String longAnalysis,
            List<MaterialImageCandidate> imageCandidates
    ) {
        Set<String> allowedImageUrls = new LinkedHashSet<>();
        if (imageCandidates != null) {
            imageCandidates.stream()
                    .filter(candidate -> candidate != null && candidate.url() != null && !candidate.url().isBlank())
                    .map(candidate -> candidate.url().trim())
                    .forEach(allowedImageUrls::add);
        }

        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(longAnalysis);
        while (matcher.find()) {
            String imageUrl = matcher.group(1);
            if (!allowedImageUrls.contains(imageUrl)) {
                throw parseFailed("long_analysis_image_url_invalid");
            }
        }
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

    private record PendingSection(
            int index,
            String heading,
            int headingStart,
            int contentStart
    ) {
        private MarkdownSection toSection(int end, String longAnalysis) {
            return new MarkdownSection(
                    index,
                    heading,
                    longAnalysis.substring(contentStart, end),
                    headingStart,
                    contentStart,
                    end
            );
        }
    }

    private record MarkdownSection(
            int index,
            String heading,
            String body,
            int headingStart,
            int contentStart,
            int end
    ) {
    }

    private record FallbackImagePlacement(
            MaterialImageCandidate candidate,
            MarkdownSection section,
            int score,
            int candidateIndex,
            int sectionIndex
    ) {
    }

}
