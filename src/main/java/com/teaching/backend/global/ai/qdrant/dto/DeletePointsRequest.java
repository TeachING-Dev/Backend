package com.teaching.backend.global.ai.qdrant.dto;

import java.util.List;

public record DeletePointsRequest(List<String> points) {
}
