package com.teaching.backend.global.ai.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Message(String role, String content) {
        }
    }
}
