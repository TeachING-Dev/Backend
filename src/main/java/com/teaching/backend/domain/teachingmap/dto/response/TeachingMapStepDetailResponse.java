package com.teaching.backend.domain.teachingmap.dto.response;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.teachingmap.entity.TeachingMapStep;

import java.util.List;

public record TeachingMapStepDetailResponse(
        Long stepId,
        Long materialId,
        Integer stepNumber,
        String title,
        String createdAt,
        List<String> tags,
        String originalUrl,
        ExistingAiAnalysis existingAiAnalysis,
        AiTeacherAnalysis aiTeacherAnalysis
) {
    public static TeachingMapStepDetailResponse of(TeachingMapStep step, Material material, List<String> tags,
                                                   ExistingAiAnalysis existingAiAnalysis, AiTeacherAnalysis aiTeacherAnalysis) {
        return new TeachingMapStepDetailResponse(
                step.getId(),
                material.getId(),
                step.getStepOrder(),
                step.getStepTitle(),
                material.getCreatedAt().toString(),
                tags,
                material.getOriginalUrl(),
                existingAiAnalysis,
                aiTeacherAnalysis
        );
    }
}