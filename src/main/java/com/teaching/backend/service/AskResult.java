package com.teaching.backend.service;

import com.teaching.backend.entity.ChatMessage;
import com.teaching.backend.entity.ChatRoom;

public record AskResult(
        ChatRoom chatRoom,
        ChatMessage userMessage,
        ChatMessage aiMessage,
        Integer remainingCount
) {
}
