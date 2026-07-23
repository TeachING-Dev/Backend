package com.teaching.backend.domain.home.service;

import com.teaching.backend.domain.home.dto.HomeDashboardResponse;
import com.teaching.backend.domain.home.dto.HomeMaterialResponse;
import com.teaching.backend.domain.home.dto.HomeTeachingMapResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
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

    private static final int RECENT_MATERIAL_LIMIT = 6;
    private static final int ACTIVE_TEACHING_MAP_LIMIT = 3;

    private final MaterialRepository materialRepository;
    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final TeachingMapRepository teachingMapRepository;

    public HomeDashboardResponse getDashboard(Long userId) {
        validateUserId(userId);

        List<Material> materials = materialRepository.findAllByUser_Id(
                userId,
                PageRequest.of(0, RECENT_MATERIAL_LIMIT, recentSort())
        ).getContent();

        List<HomeMaterialResponse> recentMaterials = createMaterialResponses(materials);
        List<HomeTeachingMapResponse> activeTeachingMaps = teachingMapRepository
                .findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                        userId,
                        TeachingMapStatus.IN_PROGRESS,
                        PageRequest.of(0, ACTIVE_TEACHING_MAP_LIMIT, recentSort())
                )
                .getContent()
                .stream()
                .map(HomeTeachingMapResponse::from)
                .toList();

        return HomeDashboardResponse.of(recentMaterials, activeTeachingMaps);
    }

    private List<HomeMaterialResponse> createMaterialResponses(List<Material> materials) {
        if (materials.isEmpty()) {
            return List.of();
        }

        List<Long> materialIds = materials.stream()
                .map(Material::getId)
                .toList();

        Map<Long, String> summaryByMaterialId = materialAnalysisRepository.findAllActiveByMaterialIds(materialIds)
                .stream()
                .collect(Collectors.toMap(
                        analysis -> analysis.getMaterial().getId(),
                        MaterialAnalysis::getSummary,
                        (current, ignored) -> current
                ));

        return materials.stream()
                .map(material -> HomeMaterialResponse.of(
                        material,
                        summaryByMaterialId.get(material.getId())
                ))
                .toList();
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
