package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.dto.extract.MaterialImageCandidate;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MaterialUrlAnalysisPromptBuilder {

    private static final String NO_INFORMATION = "정보 없음";
    private static final String NO_FOLDER_MESSAGE = "현재 사용자의 폴더 목록이 없습니다.";
    private static final String NO_IMAGE_CANDIDATES_MESSAGE = "없음";
    private static final int MAX_IMAGE_URL_LENGTH = 2_048;
    private static final int MAX_IMAGE_METADATA_LENGTH = 300;
    private static final int MAX_IMAGE_CANDIDATES_BLOCK_LENGTH = 8_000;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^{}]+)}}");

    private final MaterialUrlAnalysisPromptProvider promptProvider;

    public String buildSystemMessage() {
        return promptProvider.systemPrompt();
    }

    public String buildUserMessage(
            ExtractedMaterialContent content,
            List<String> folderNames
    ) {
        Map<String, String> replacements = Map.of(
                "ORIGINAL_URL", valueOrNoInformation(content.originalUrl()),
                "TITLE", valueOrNoInformation(content.title()),
                "PLATFORM_TYPE", content.platformType() == null ? NO_INFORMATION : content.platformType().name(),
                "AUTHOR", valueOrNoInformation(content.author()),
                "PUBLISHED_AT", valueOrNoInformation(content.publishedAt()),
                "FOLDER_LIST", formatFolderList(folderNames),
                "EXTRACTED_CONTENT", valueOrNoInformation(content.content()),
                "IMAGE_CANDIDATES", formatImageCandidates(content.imageCandidates())
        );

        return replacePlaceholders(promptProvider.userTemplate(), replacements);
    }

    private String replacePlaceholders(
            String template,
            Map<String, String> replacements
    ) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = replacements.get(key);
            if (replacement == null) {
                throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String formatFolderList(List<String> folderNames) {
        Set<String> names = new LinkedHashSet<>();
        if (folderNames != null) {
            folderNames.stream()
                    .map(name -> name == null ? "" : name.trim())
                    .filter(name -> !name.isBlank())
                    .forEach(names::add);
        }

        if (names.isEmpty()) {
            return NO_FOLDER_MESSAGE;
        }

        return names.stream()
                .map(name -> "- " + name)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse(NO_FOLDER_MESSAGE);
    }

    private String formatImageCandidates(List<MaterialImageCandidate> imageCandidates) {
        if (imageCandidates == null || imageCandidates.isEmpty()) {
            return NO_IMAGE_CANDIDATES_MESSAGE;
        }

        StringBuilder builder = new StringBuilder();
        int displayedIndex = 1;
        for (int index = 0; index < imageCandidates.size(); index++) {
            MaterialImageCandidate candidate = imageCandidates.get(index);
            if (candidate == null || candidate.url() == null || candidate.url().isBlank()) {
                continue;
            }
            String url = candidate.url().trim();
            if (url.length() > MAX_IMAGE_URL_LENGTH) {
                continue;
            }
            String candidateBlock = formatImageCandidate(displayedIndex, candidate, url);
            int separatorLength = builder.isEmpty() ? 0 : System.lineSeparator().length() * 2;
            if (builder.length() + separatorLength + candidateBlock.length() > MAX_IMAGE_CANDIDATES_BLOCK_LENGTH) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append(System.lineSeparator()).append(System.lineSeparator());
            }
            builder.append("이미지 후보 ").append(displayedIndex).append(System.lineSeparator());
            appendImageCandidateLine(builder, "URL", url, false);
            appendImageCandidateLine(builder, "ALT", candidate.alt(), true);
            appendImageCandidateLine(builder, "CAPTION", candidate.caption(), true);
            appendImageCandidateLine(builder, "TITLE", candidate.title(), true);
            appendImageCandidateLine(builder, "SECTION", candidate.sectionHeading(), true);
            appendImageCandidateLine(builder, "CONTEXT", candidate.context(), true);
            displayedIndex++;
        }

        return builder.isEmpty() ? NO_IMAGE_CANDIDATES_MESSAGE : builder.toString();
    }

    private String formatImageCandidate(int index, MaterialImageCandidate candidate, String url) {
        StringBuilder builder = new StringBuilder();
        builder.append("이미지 후보 ").append(index).append(System.lineSeparator());
        appendImageCandidateLine(builder, "URL", url, false);
        appendImageCandidateLine(builder, "ALT", candidate.alt(), true);
        appendImageCandidateLine(builder, "CAPTION", candidate.caption(), true);
        appendImageCandidateLine(builder, "TITLE", candidate.title(), true);
        appendImageCandidateLine(builder, "SECTION", candidate.sectionHeading(), true);
        appendImageCandidateLine(builder, "CONTEXT", candidate.context(), true);
        return builder.toString();
    }

    private void appendImageCandidateLine(StringBuilder builder, String label, String value) {
        appendImageCandidateLine(builder, label, value, true);
    }

    private void appendImageCandidateLine(StringBuilder builder, String label, String value, boolean truncate) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim();
        if (truncate) {
            normalized = truncate(normalized, MAX_IMAGE_METADATA_LENGTH);
        }
        builder.append("- ")
                .append(label)
                .append(": ")
                .append(normalized)
                .append(System.lineSeparator());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private String valueOrNoInformation(String value) {
        return value == null || value.isBlank() ? NO_INFORMATION : value.trim();
    }

    private String valueOrNoInformation(LocalDateTime value) {
        return value == null ? NO_INFORMATION : value.toString();
    }
}
