package com.teaching.backend.domain.teachingmap.dto.request;

import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TeachingMapCreateRequest (
    @NotBlank String title,
    @NotBlank String description,
    @NotNull
    Long folderId,
    @NotNull
    TeachingMapType type// SHORT_CUT,DEEP_DIVE
){}
