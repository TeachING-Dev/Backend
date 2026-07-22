package com.teaching.backend.domain.material.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// OpenAI 응답 문자열을 MaterialAiAnalysisResult로 파싱/검증하는 컴포넌트.
// response_format=json_object로 요청하지만, 모델이 코드펜스를 덧붙이는 경우까지 방어적으로 처리한다.
// Spring Boot 4는 기본적으로 Jackson 3(tools.jackson.databind.ObjectMapper) 빈만 자동 구성하므로,
// 이 클래스 전용으로 com.fasterxml.jackson(Jackson 2) ObjectMapper를 직접 소유한다.
@Component
public class MaterialAiAnalysisResponseParser {

    private static final Pattern CODE_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MaterialAiAnalysisResult parse(String rawContent) {
        String jsonText = stripCodeFence(rawContent);

        MaterialAiAnalysisResult result;
        try {
            result = objectMapper.readValue(jsonText, MaterialAiAnalysisResult.class);
        } catch (Exception e) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
        }

        if (result.shortSummary() == null || result.shortSummary().isBlank()
                || result.longAnalysis() == null || result.longAnalysis().isBlank()) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
        }

        List<String> tags = result.tags() == null ? List.of() : result.tags();

        return new MaterialAiAnalysisResult(
                result.shortSummary().trim(),
                result.longAnalysis().trim(),
                tags,
                result.highlights(),
                result.recommendedFolder()
        );
    }

    private String stripCodeFence(String rawContent) {
        if (rawContent == null) {
            return null;
        }

        Matcher matcher = CODE_FENCE_PATTERN.matcher(rawContent.strip());
        return matcher.matches() ? matcher.group(1) : rawContent.strip();
    }
}
