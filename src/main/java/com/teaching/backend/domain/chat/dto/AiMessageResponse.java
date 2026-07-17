package com.teaching.backend.domain.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

// 질문(Ask) 응답에 포함되는 AI 메시지 DTO
public record AiMessageResponse(
        Long messageId,
        String content,
        Boolean isFallback,
        List<ChatSourceResponse> sources,
        LocalDateTime createdAt
) {
}
