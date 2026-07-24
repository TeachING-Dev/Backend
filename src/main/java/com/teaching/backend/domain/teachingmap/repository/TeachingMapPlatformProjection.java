package com.teaching.backend.domain.teachingmap.repository;

import com.teaching.backend.domain.material.enums.PlatformType;

public interface TeachingMapPlatformProjection {
    Long getTeachingMapId();
    PlatformType getPlatformType();
}