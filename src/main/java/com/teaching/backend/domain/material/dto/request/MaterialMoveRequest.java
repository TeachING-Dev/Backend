package com.teaching.backend.domain.material.dto.request;

import java.util.List;

public record MaterialMoveRequest(
        List<Long> materialIds,
        Long targetFolderId
) {
}
