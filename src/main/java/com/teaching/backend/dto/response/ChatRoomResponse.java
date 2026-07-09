package com.teaching.backend.dto.response;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long chatroomId,
        String title,
        LocalDateTime createdAt
) {
}
