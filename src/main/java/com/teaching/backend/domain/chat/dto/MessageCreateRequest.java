package com.teaching.backend.domain.chat.dto;

// 메시지 생성(질문) 요청 DTO
public record MessageCreateRequest(
        String content
) {
}
