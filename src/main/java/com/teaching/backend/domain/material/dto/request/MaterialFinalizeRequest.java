package com.teaching.backend.domain.material.dto.request;

import java.util.List;

public record MaterialFinalizeRequest(
        Long folderId,
        List<Long> tagIds
) {
}
