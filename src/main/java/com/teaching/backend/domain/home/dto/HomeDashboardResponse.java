package com.teaching.backend.domain.home.dto;

import java.util.List;

public record HomeDashboardResponse(
        List<HomeMaterialResponse> recentMaterials,
        List<HomeTeachingMapResponse> activeTeachingMaps
) {

    public static HomeDashboardResponse of(
            List<HomeMaterialResponse> recentMaterials,
            List<HomeTeachingMapResponse> activeTeachingMaps
    ) {
        return new HomeDashboardResponse(recentMaterials, activeTeachingMaps);
    }
}
