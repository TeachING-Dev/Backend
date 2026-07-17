package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.chat.entity.ChatMessage;
import com.teaching.backend.domain.chat.entity.ChatRoom;
import com.teaching.backend.domain.chat.entity.ChatSource;

import java.util.List;

// 질문(Ask) 처리 결과(채팅방, 유저 메시지, AI 메시지, AI 메시지 출처, 잔여 질문 횟수)를 담는 서비스 내부 전달 객체
public record AskResult(
        ChatRoom chatRoom,
        ChatMessage userMessage,
        ChatMessage aiMessage,
        List<ChatSource> aiMessageSources,
        Integer remainingCount
) {
}
