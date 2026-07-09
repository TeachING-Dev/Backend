package com.teaching.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AiMessageResponse(
        Long messageId,
        String content,
        Boolean isFallback,
        List<ChatSourceResponse> sources,
        LocalDateTime createdAt
) {
}
