package com.teaching.backend.domain.material.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Maps the URL material analysis JSON returned by OpenAI.
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaterialAiAnalysisResult(
        @JsonProperty("short_summary") String shortSummary,
        @JsonProperty("long_analysis") String longAnalysis,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("recommended_folder") String recommendedFolder
) {
}
