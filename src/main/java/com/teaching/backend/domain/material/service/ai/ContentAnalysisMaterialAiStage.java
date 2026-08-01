package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiStageContext;
import com.teaching.backend.domain.material.dto.ai.MaterialAiStageResult;
import com.teaching.backend.domain.material.dto.ai.MaterialUrlAnalysisParseResult;
import com.teaching.backend.domain.material.enums.MaterialAiStageType;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.service.MaterialAiAnalysisResponseParser;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentAnalysisMaterialAiStage implements MaterialAiAnalysisStage {

    private static final int MAX_PARSE_ATTEMPTS = 2;

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
        PromptLengthDiagnostic diagnostic = promptLengthDiagnostic(context, folderNames, systemPrompt, userMessage);
        log.info(
                "URL AI analysis OpenAI request prepared. userId={}, platformType={}, url={}, extractedContentLength={}, systemPromptLength={}, userMessageLength={}, folderCount={}, folderContextLength={}, totalPromptLength={}",
                context.userId(),
                diagnostic.platformType(),
                diagnostic.sanitizedUrl(),
                diagnostic.extractedContentLength(),
                diagnostic.systemPromptLength(),
                diagnostic.userMessageLength(),
                diagnostic.folderCount(),
                diagnostic.folderContextLength(),
                diagnostic.totalPromptLength()
        );
        MaterialUrlAnalysisParseResult parseResult = executeAndParse(systemPrompt, userMessage, folderNames);
        MaterialAiAnalysisResult result = parseResult.analysisResult();

        return new MaterialAiStageResult(
                type(),
                result,
                parseResult.highlights(),
                parseResult.recommendedFolderName()
        );
    }

    private MaterialUrlAnalysisParseResult executeAndParse(
            String systemPrompt,
            String userMessage,
            List<String> folderNames
    ) {
        MaterialException lastParseFailure = null;
        for (int attempt = 1; attempt <= MAX_PARSE_ATTEMPTS; attempt++) {
            String rawResponse = openAiRetryExecutor.execute(
                    () -> openAiClient.chatCompleteJson(systemPrompt, userMessage)
            );
            try {
                return materialAiAnalysisResponseParser.parseUrlAnalysis(rawResponse, folderNames);
            } catch (MaterialException e) {
                if (e.getErrorCode() != MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED
                        || attempt == MAX_PARSE_ATTEMPTS) {
                    throw e;
                }
                lastParseFailure = e;
            }
        }

        throw lastParseFailure;
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

    static PromptLengthDiagnostic promptLengthDiagnostic(
            MaterialAiStageContext context,
            List<String> folderNames,
            String systemPrompt,
            String userMessage
    ) {
        String content = context == null || context.extractedContent() == null
                ? null
                : context.extractedContent().content();
        PlatformType platformType = context == null ? null : context.platformType();
        String originalUrl = context == null ? null : context.originalUrl();
        int systemPromptLength = length(systemPrompt);
        int userMessageLength = length(userMessage);
        int folderCount = folderNames == null ? 0 : folderNames.size();
        int folderContextLength = folderNames == null
                ? 0
                : folderNames.stream()
                .mapToInt(ContentAnalysisMaterialAiStage::length)
                .sum();

        return new PromptLengthDiagnostic(
                length(content),
                systemPromptLength,
                userMessageLength,
                folderCount,
                folderContextLength,
                systemPromptLength + userMessageLength,
                platformType,
                sanitizeUrl(originalUrl)
        );
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(url);
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null
            ).toString();
        } catch (Exception ignored) {
            int queryStart = url.indexOf('?');
            return queryStart < 0 ? url : url.substring(0, queryStart);
        }
    }

    record PromptLengthDiagnostic(
            int extractedContentLength,
            int systemPromptLength,
            int userMessageLength,
            int folderCount,
            int folderContextLength,
            int totalPromptLength,
            PlatformType platformType,
            String sanitizedUrl
    ) {
    }
}
