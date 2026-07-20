package com.teaching.backend.global.ai.qdrant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QdrantSearchHit(String id, double score, Map<String, Object> payload) {
}
