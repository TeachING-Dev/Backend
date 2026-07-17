package com.teaching.backend.global.ai.qdrant.dto;

import java.util.List;
import java.util.Map;

public record UpsertPointsRequest(List<Point> points) {

    public record Point(String id, float[] vector, Map<String, Object> payload) {
    }
}
