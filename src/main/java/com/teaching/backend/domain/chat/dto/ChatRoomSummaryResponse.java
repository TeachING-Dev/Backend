package com.teaching.backend.domain.chat.dto;

import java.time.LocalDateTime;

// 채팅방 목록의 개별 항목 응답 DTO
public record ChatRoomSummaryResponse(
        Long chatroomId,
        String title,
        LocalDateTime lastMessageAt
) {
}
