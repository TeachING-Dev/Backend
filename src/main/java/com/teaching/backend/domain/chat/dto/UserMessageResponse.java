package com.teaching.backend.domain.chat.dto;

import java.time.LocalDateTime;

// 질문(Ask) 응답에 포함되는 유저 메시지 DTO
public record UserMessageResponse(
        Long messageId,
        String content,
        LocalDateTime createdAt
) {
}
