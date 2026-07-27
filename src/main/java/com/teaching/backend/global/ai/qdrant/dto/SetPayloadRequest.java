package com.teaching.backend.global.ai.qdrant.dto;

import java.util.List;
import java.util.Map;

public record SetPayloadRequest(
        Map<String, Object> payload,
        List<String> points
) {
}
