package com.teaching.backend.global.ai.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        double temperature,
        @JsonProperty("response_format")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Map<String, String> responseFormat
) {

    public ChatCompletionRequest(String model, List<Message> messages, double temperature) {
        this(model, messages, temperature, null);
    }

    public static ChatCompletionRequest jsonMode(String model, List<Message> messages, double temperature) {
        return new ChatCompletionRequest(model, messages, temperature, Map.of("type", "json_object"));
    }

    public record Message(String role, String content) {
    }
}
