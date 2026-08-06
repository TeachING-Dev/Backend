package com.teaching.backend.domain.home.service;

import com.teaching.backend.domain.home.dto.HomeDashboardResponse;
import com.teaching.backend.domain.home.dto.HomeMaterialResponse;
import com.teaching.backend.domain.home.dto.HomeTeachingMapResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.teachingmap.dto.response.SourcePlatform;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapPlatformProjection;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapStepRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private static final int RECENT_MATERIAL_LIMIT = 5;
    private static final int ACTIVE_TEACHING_MAP_LIMIT = 3;
    private static final int SOURCE_PLATFORM_LIMIT = 3;

    private final MaterialRepository materialRepository;
    private final TeachingMapRepository teachingMapRepository;
    private final TeachingMapStepRepository teachingMapStepRepository;


    public HomeDashboardResponse getDashboard(Long userId) {
        validateUserId(userId);

        List<Material> materials = materialRepository.findHomeRecentMaterials(
                userId,
                AiStatus.COMPLETED,
                PageRequest.of(0, RECENT_MATERIAL_LIMIT, recentSort())
        ).getContent();

        List<HomeMaterialResponse> recentMaterials = createMaterialResponses(materials);
        List<TeachingMap> teachingMaps = teachingMapRepository
                .findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                        userId,
                        TeachingMapStatus.IN_PROGRESS,
                        PageRequest.of(0, ACTIVE_TEACHING_MAP_LIMIT, recentSort())
                )
                .getContent();
        List<HomeTeachingMapResponse> activeTeachingMaps = createTeachingMapResponses(userId, teachingMaps);

        return HomeDashboardResponse.of(recentMaterials, activeTeachingMaps);
    }

    private List<HomeMaterialResponse> createMaterialResponses(List<Material> materials) {
        return materials.stream()
                .map(HomeMaterialResponse::from)
                .toList();
    }

    private List<HomeTeachingMapResponse> createTeachingMapResponses(Long userId, List<TeachingMap> teachingMaps) {
        if (teachingMaps.isEmpty()) {
            return List.of();
        }

        List<Long> teachingMapIds = teachingMaps.stream()
                .map(TeachingMap::getId)
                .toList();

        Map<Long, List<PlatformType>> platformTypesByTeachingMapId = teachingMapStepRepository
                .findActivePlatformTypesByTeachingMapIdIn(teachingMapIds, userId)
                .stream()
                .collect(Collectors.groupingBy(
                        TeachingMapPlatformProjection::getTeachingMapId,
                        Collectors.mapping(TeachingMapPlatformProjection::getPlatformType, Collectors.toList())
                ));

        return teachingMaps.stream()
                .map(teachingMap -> HomeTeachingMapResponse.from(
                        teachingMap,
                        toSourcePlatforms(platformTypesByTeachingMapId.getOrDefault(teachingMap.getId(), List.of()))
                ))
                .toList();
    }

    private List<SourcePlatform> toSourcePlatforms(List<PlatformType> platformTypes) {
        return platformTypes.stream()
                .filter(platformType -> platformType != null && platformType.getIconPath() != null)
                .distinct()
                .limit(SOURCE_PLATFORM_LIMIT)
                .map(platformType -> new SourcePlatform(
                        platformType.name(),
                        buildIconUrl(platformType.getIconPath())
                ))
                .toList();
    }

    private String buildIconUrl(String iconPath) {
        return iconPath;
    }

    private Sort recentSort() {
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }
    }
}
