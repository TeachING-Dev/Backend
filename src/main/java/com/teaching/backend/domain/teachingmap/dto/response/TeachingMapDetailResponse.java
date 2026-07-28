package com.teaching.backend.domain.teachingmap.dto.response;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.entity.TeachingMapStep;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public record TeachingMapDetailResponse (
        Long teachingMapId,
        Long folderId,
        String title,
        String description,
        TeachingMapType type,
        int currentSteps,
        int totalSteps,
        List<StepSummary> steps
){
    public record StepSummary (
            Long stepId,
            int order,
            String tip,
            String stepTitle,
            boolean isFinished
    ){}


    public static TeachingMapDetailResponse from(TeachingMap teachingMap,List<TeachingMapStep> steps) {
        List<StepSummary> stepSummaries = steps.stream()
                .map(step -> new StepSummary(
                        step.getId(),
                        step.getStepOrder(),
                        step.getTip(),
                        step.getStepTitle(),
                        step.getIsFinished()
                ))
                .toList();

        return new TeachingMapDetailResponse(
                teachingMap.getId(),
                teachingMap.getFolder().getId(),
                teachingMap.getTitle(),
                teachingMap.getDescription(),
                teachingMap.getType(),
                teachingMap.getCurrentSteps(),
                teachingMap.getTotalSteps(),
                stepSummaries
        );
    }

}
