package com.teaching.backend.domain.chat.dto;

// 질문(Ask) 요청에 대한 전체 응답 DTO
public record AskResponse(
        String chatroomTitle,
        UserMessageResponse userMessage,
        AiMessageResponse aiMessage,
        Integer remainingCount
) {
}
