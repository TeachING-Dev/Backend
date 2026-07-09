package com.teaching.backend.dto.response;

import java.time.LocalDateTime;

public record ChatRoomSummaryResponse(
        Long chatroomId,
        String title,
        LocalDateTime lastMessageAt
) {
}
