package com.teaching.backend.global.ai.qdrant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QdrantSearchResponse(List<QdrantSearchHit> result) {
}
