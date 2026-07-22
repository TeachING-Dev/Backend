package com.teaching.backend.domain.material.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// OpenAI가 반환한 자료 분석 JSON 응답 매핑. highlights/recommendedFolder는 이번 범위(short_summary/long_analysis/tags)
// 밖이라 파싱만 하고 사용하지 않는다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaterialAiAnalysisResult(
        @JsonProperty("short_summary") String shortSummary,
        @JsonProperty("long_analysis") String longAnalysis,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("highlights") List<Highlight> highlights,
        @JsonProperty("recommended_folder") String recommendedFolder
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Highlight(String text, String type) {
    }
}
