package com.teaching.backend.domain.teachingmap.dto.request;

import java.util.List;

public record TeachingMapIdsRequest(
        List<Long> teachingMapIds
) {
}
