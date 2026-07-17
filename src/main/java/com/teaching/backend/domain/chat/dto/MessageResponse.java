package com.teaching.backend.domain.chat.dto;

import com.teaching.backend.domain.chat.enums.ChatRole;

import java.time.LocalDateTime;
import java.util.List;

// 채팅방 히스토리에 포함되는 개별 메시지 응답 DTO
public record MessageResponse(
        Long messageId,
        ChatRole role,
        String content,
        Boolean isFallback,
        List<ChatSourceResponse> sources,
        LocalDateTime createdAt
) {
}
