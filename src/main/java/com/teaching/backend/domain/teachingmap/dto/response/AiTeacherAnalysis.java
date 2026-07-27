package com.teaching.backend.domain.teachingmap.dto.response;

import com.teaching.backend.domain.teachingmap.enums.GuideType;

import java.util.List;

public record AiTeacherAnalysis(String guideType, String teacherProfileImage, List<FeedbackResponse> feedbacks) {
    public static AiTeacherAnalysis of(GuideType guideType, String teacherProfileImage, List<FeedbackResponse> feedbacks) {
        return new AiTeacherAnalysis(guideType.name(), teacherProfileImage, feedbacks);
    }
}
