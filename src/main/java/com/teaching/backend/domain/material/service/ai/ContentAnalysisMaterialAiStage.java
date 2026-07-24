package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiStageContext;
import com.teaching.backend.domain.material.dto.ai.MaterialAiStageResult;
import com.teaching.backend.domain.material.dto.ai.MaterialUrlAnalysisParseResult;
import com.teaching.backend.domain.material.enums.MaterialAiStageType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.service.MaterialAiAnalysisResponseParser;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ContentAnalysisMaterialAiStage implements MaterialAiAnalysisStage {

    private final OpenAiClient openAiClient;
    private final MaterialUrlAnalysisPromptBuilder materialUrlAnalysisPromptBuilder;
    private final MaterialAiAnalysisResponseParser materialAiAnalysisResponseParser;
    private final OpenAiRetryExecutor openAiRetryExecutor;
    private final FolderRepository folderRepository;

    @Override
    public MaterialAiStageType type() {
        return MaterialAiStageType.CONTENT_ANALYSIS;
    }

    @Override
    public MaterialAiStageResult execute(MaterialAiStageContext context) {
        validateExtractedContent(context);
        List<String> folderNames = findActiveFolderNames(context.userId());
        String systemPrompt = materialUrlAnalysisPromptBuilder.buildSystemMessage();
        String userMessage = materialUrlAnalysisPromptBuilder.buildUserMessage(context.extractedContent(), folderNames);
        String rawResponse = openAiRetryExecutor.execute(
                () -> openAiClient.chatCompleteJson(systemPrompt, userMessage)
        );
        MaterialUrlAnalysisParseResult parseResult =
                materialAiAnalysisResponseParser.parseUrlAnalysis(rawResponse, folderNames);
        MaterialAiAnalysisResult result = parseResult.analysisResult();

        return new MaterialAiStageResult(
                type(),
                result,
                parseResult.highlights(),
                parseResult.recommendedFolderName()
        );
    }

    private List<String> findActiveFolderNames(Long userId) {
        return new LinkedHashSet<>(folderRepository.findAllByUser_Id(
                        userId,
                        Sort.by(Sort.Direction.ASC, "name")
                ).stream()
                .map(Folder::getName)
                .map(name -> name == null ? "" : name.trim())
                .filter(name -> !name.isBlank())
                .toList())
                .stream()
                .toList();
    }

    private void validateExtractedContent(MaterialAiStageContext context) {
        if (context.extractedContent() == null
                || context.extractedContent().content() == null
                || context.extractedContent().content().isBlank()) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
        }
    }
}
