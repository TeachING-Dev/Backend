package com.teaching.backend.global.ai.qdrant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SearchRequest(
        float[] vector,
        int limit,
        Filter filter,
        @JsonProperty("with_payload") boolean withPayload
) {

    public record Filter(List<Condition> must) {

        public record Condition(String key, Match match) {

            public record Match(Object value) {
            }
        }
    }
}
