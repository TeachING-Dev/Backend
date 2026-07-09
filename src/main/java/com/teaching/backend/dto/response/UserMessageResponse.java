package com.teaching.backend.dto.response;

import java.time.LocalDateTime;

public record UserMessageResponse(
        Long messageId,
        String content,
        LocalDateTime createdAt
) {
}
