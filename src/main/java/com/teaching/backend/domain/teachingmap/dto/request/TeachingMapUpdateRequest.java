package com.teaching.backend.domain.teachingmap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeachingMapUpdateRequest(
        @NotBlank @Size(max = 30, message = "제목은 최대 30자까지 입력 가능합니다.") String title,
        @NotBlank @Size(max = 150, message = "설명은 최대 150자까지 입력 가능합니다.") String description
) {}