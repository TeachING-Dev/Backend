package com.teaching.backend.domain.teachingmap.dto.response;

import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;

import java.time.LocalDateTime;
import java.util.List;

public record TeachingMapListItem(
        Long teachingMapId,
        String title,
        String description,
        Boolean isDraft,
        String status,
        String type,
        List<SourcePlatform> sourcePlatforms,
        int extraCount,
        int totalStepCount,
        int completedStepCount,
        double progressRate,
        LocalDateTime createdAt
) {

    public static TeachingMapListItem from(TeachingMap teachingMap, boolean isDraft,
                                           List<SourcePlatform> sourcePlatforms, int extraCount) {
        int totalStepCount = isDraft ? 0 : teachingMap.getTotalSteps();
        int completedStepCount = isDraft ? 0 : teachingMap.getCurrentSteps();
        double progressRate = isDraft || totalStepCount == 0
                ? 0.0
                : Math.round((completedStepCount * 1000.0 / totalStepCount)) / 10.0;

        return new TeachingMapListItem(
                teachingMap.getId(),
                teachingMap.getTitle(),
                teachingMap.getDescription(),
                teachingMap.getIsDraft(),
                isDraft ? null : teachingMap.getStatus().name(),
                teachingMap.getType().name(),
                sourcePlatforms,
                extraCount,
                totalStepCount,
                completedStepCount,
                progressRate,
                teachingMap.getCreatedAt()
        );
    }
}