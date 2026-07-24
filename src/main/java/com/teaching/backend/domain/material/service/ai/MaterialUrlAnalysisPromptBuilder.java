package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MaterialUrlAnalysisPromptBuilder {

    private static final String NO_INFORMATION = "정보 없음";
    private static final String NO_FOLDER_MESSAGE = "현재 사용자의 폴더 목록이 없습니다.";
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{\\{[A-Z_]+}}");

    private final MaterialUrlAnalysisPromptProvider promptProvider;

    public String buildSystemMessage() {
        return promptProvider.systemPrompt();
    }

    public String buildUserMessage(
            ExtractedMaterialContent content,
            List<String> folderNames
    ) {
        String userMessage = promptProvider.userTemplate();
        userMessage = userMessage.replace("{{ORIGINAL_URL}}", valueOrNoInformation(content.originalUrl()));
        userMessage = userMessage.replace("{{TITLE}}", valueOrNoInformation(content.title()));
        userMessage = userMessage.replace("{{PLATFORM_TYPE}}", content.platformType() == null ? NO_INFORMATION : content.platformType().name());
        userMessage = userMessage.replace("{{AUTHOR}}", valueOrNoInformation(content.author()));
        userMessage = userMessage.replace("{{PUBLISHED_AT}}", valueOrNoInformation(content.publishedAt()));
        userMessage = userMessage.replace("{{FOLDER_LIST}}", formatFolderList(folderNames));

        String withoutContentPlaceholder = userMessage.replace("{{EXTRACTED_CONTENT}}", "");
        if (UNRESOLVED_PLACEHOLDER.matcher(withoutContentPlaceholder).find()) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
        }
        userMessage = userMessage.replace("{{EXTRACTED_CONTENT}}", valueOrNoInformation(content.content()));
        return userMessage;
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

    private String valueOrNoInformation(String value) {
        return value == null || value.isBlank() ? NO_INFORMATION : value.trim();
    }

    private String valueOrNoInformation(LocalDateTime value) {
        return value == null ? NO_INFORMATION : value.toString();
    }
}
