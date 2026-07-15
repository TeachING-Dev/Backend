package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.chat.entity.ChatMessage;
import com.teaching.backend.domain.chat.entity.ChatRoom;
import com.teaching.backend.domain.chat.entity.ChatSource;

import java.util.List;
import java.util.Map;

// 채팅방 정보, 메시지 목록, 메시지별 출처(ChatSource) 목록을 함께 담는 서비스 내부 전달 객체
public record ChatRoomHistoryResult(
        ChatRoom chatRoom,
        List<ChatMessage> messages,
        Map<Long, List<ChatSource>> sourcesByMessageId
) {
}
