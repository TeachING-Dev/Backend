package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.teachingmap.dto.response.SourcePlatform;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;

import java.time.LocalDateTime;
import java.util.List;

public record TrashTeachingMapItemResponse(
        Long teachingMapId,
        String title,
        String status,
        String type,
        List<SourcePlatform> sourcePlatforms,
        int extraCount,
        int totalStepCount,
        int completedStepCount,
        double progressRate,
        LocalDateTime deletedAt
) {

    private static final int SOURCE_PLATFORM_DISPLAY_LIMIT = 3;

    public static TrashTeachingMapItemResponse from(
            TeachingMap teachingMap,
            List<SourcePlatform> allSourcePlatforms
    ) {
        boolean isDraft = Boolean.TRUE.equals(teachingMap.getIsDraft());
        int totalStepCount = isDraft ? 0 : teachingMap.getTotalSteps();
        int completedStepCount = isDraft ? 0 : teachingMap.getCurrentSteps();
        double progressRate = isDraft || totalStepCount == 0
                ? 0.0
                : Math.round((completedStepCount * 1000.0 / totalStepCount)) / 10.0;

        List<SourcePlatform> sourcePlatforms = allSourcePlatforms.stream()
                .limit(SOURCE_PLATFORM_DISPLAY_LIMIT)
                .toList();
        int extraCount = Math.max(0, allSourcePlatforms.size() - SOURCE_PLATFORM_DISPLAY_LIMIT);

        return new TrashTeachingMapItemResponse(
                teachingMap.getId(),
                teachingMap.getTitle(),
                isDraft ? null : teachingMap.getStatus().name(),
                teachingMap.getType().name(),
                sourcePlatforms,
                extraCount,
                totalStepCount,
                completedStepCount,
                progressRate,
                teachingMap.getDeletedAt()
        );
    }
}
