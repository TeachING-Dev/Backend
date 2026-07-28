package com.teaching.backend.domain.teachingmap.dto.response;

import com.teaching.backend.domain.teachingmap.entity.AiGuide;

public record FeedbackResponse(Long aiGuideId, String promptVersion, String type, String title, String content) {
    public static FeedbackResponse from(AiGuide guide) {
        return new FeedbackResponse(guide.getId(), guide.getPromptVer(), guide.getType().name(), guide.getTitle(), guide.getContent());
    }
}
