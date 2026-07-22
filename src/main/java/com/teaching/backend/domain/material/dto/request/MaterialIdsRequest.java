package com.teaching.backend.domain.material.dto.request;

import java.util.List;

public record MaterialIdsRequest(
        List<Long> materialIds
) {
}
