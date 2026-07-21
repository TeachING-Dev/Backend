package com.teaching.backend.domain.chat.dto;

import java.time.LocalDateTime;

// 채팅방 생성 응답 DTO
public record ChatRoomResponse(
        Long chatroomId,
        String title,
        LocalDateTime createdAt
) {
}
