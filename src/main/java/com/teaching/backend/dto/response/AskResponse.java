package com.teaching.backend.dto.response;

public record AskResponse(
        String chatroomTitle,
        UserMessageResponse userMessage,
        AiMessageResponse aiMessage,
        Integer remainingCount
) {
}
