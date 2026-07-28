package com.teaching.backend.domain.teachingmap.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TeachingMapTrashRequest(
        @NotEmpty(message = "휴지통으로 이동할 티칭맵을 선택해주세요.")
        List<Long> teachingMapIds
) {
}