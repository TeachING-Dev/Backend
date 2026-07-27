package com.teaching.backend.domain.teachingmap.dto.response;

import com.teaching.backend.domain.teachingmap.entity.AiGuide;

public record HighlightAnalysisResponse(Long aiGuideId, String promptVersion, String type, String title, String content) {
    public static HighlightAnalysisResponse from(AiGuide guide) {
        return new HighlightAnalysisResponse(guide.getId(), guide.getPromptVer(), guide.getType().name(), guide.getTitle(), guide.getContent());
    }
}