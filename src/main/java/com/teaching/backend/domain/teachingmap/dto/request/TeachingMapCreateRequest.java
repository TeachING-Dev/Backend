package com.teaching.backend.domain.teachingmap.dto.request;

import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeachingMapCreateRequest (
    @NotBlank @Size(max = 30, message = "제목은 최대 30자까지 입력 가능합니다.") String title,
    @NotBlank @Size(max = 150, message = "설명은 최대 150자까지 입력 가능합니다.") String description,
    @NotNull
    Long folderId,
    @NotNull
    TeachingMapType type// SHORT_CUT,DEEP_DIVE
){}
