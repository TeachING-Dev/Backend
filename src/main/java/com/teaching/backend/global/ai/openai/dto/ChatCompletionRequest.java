package com.teaching.backend.global.ai.openai.dto;

import java.util.List;

public record ChatCompletionRequest(String model, List<Message> messages, double temperature) {

    public record Message(String role, String content) {
    }
}
