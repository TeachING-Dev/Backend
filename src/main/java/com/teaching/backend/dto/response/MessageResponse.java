package com.teaching.backend.dto.response;

import com.teaching.backend.entity.MessageRole;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        Long messageId,
        MessageRole role,
        String content,
        Boolean isFallback,
        List<ChatSourceResponse> sources,
        LocalDateTime createdAt
) {
}
