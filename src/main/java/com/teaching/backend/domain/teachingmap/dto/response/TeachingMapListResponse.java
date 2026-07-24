package com.teaching.backend.domain.teachingmap.dto.response;

import java.util.List;

public record TeachingMapListResponse(
        String currentStatus,
        String currentType,
        String currentSort,
        List<TeachingMapListItem> teachingMaps
) {}