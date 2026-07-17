package com.teaching.backend.domain.chat.dto;

import java.util.List;

// 채팅방 히스토리(메시지 목록) 조회 응답 DTO
public record ChatRoomHistoryResponse(
        Long chatroomId,
        String title,
        List<MessageResponse> messages
) {
}
