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
        for (int index = 0; index < imageCandidates.size(); index++) {
            MaterialImageCandidate candidate = imageCandidates.get(index);
            if (candidate == null || candidate.url() == null || candidate.url().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(System.lineSeparator()).append(System.lineSeparator());
            }
            builder.append("이미지 후보 ").append(index + 1).append(System.lineSeparator());
            appendImageCandidateLine(builder, "URL", candidate.url());
            appendImageCandidateLine(builder, "ALT", candidate.alt());
            appendImageCandidateLine(builder, "CAPTION", candidate.caption());
            appendImageCandidateLine(builder, "TITLE", candidate.title());
            appendImageCandidateLine(builder, "SECTION", candidate.sectionHeading());
            appendImageCandidateLine(builder, "CONTEXT", candidate.context());
        }

        return builder.isEmpty() ? NO_IMAGE_CANDIDATES_MESSAGE : builder.toString();
    }

    private void appendImageCandidateLine(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append("- ")
                .append(label)
                .append(": ")
                .append(value.trim())
                .append(System.lineSeparator());
    }

    private String valueOrNoInformation(String value) {
        return value == null || value.isBlank() ? NO_INFORMATION : value.trim();
    }

    private String valueOrNoInformation(LocalDateTime value) {
        return value == null ? NO_INFORMATION : value.toString();
    }
}
