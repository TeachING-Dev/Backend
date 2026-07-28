package com.teaching.backend.domain.teachingmap.dto.response;

public record StepToggleResponse(

        Long stepId,
        Boolean isCompleted,
        Integer completedStepCount,
        Integer totalStepCount,
        Double progressRate
) {
    public static StepToggleResponse of(Long stepId, boolean isCompleted,
                                        int completedStepCount, int totalStepCount, double progressRate) {
        return new StepToggleResponse(stepId, isCompleted, completedStepCount, totalStepCount, progressRate);
    }
}