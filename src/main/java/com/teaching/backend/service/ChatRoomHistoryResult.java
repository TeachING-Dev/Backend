package com.teaching.backend.service;

import com.teaching.backend.entity.ChatMessage;
import com.teaching.backend.entity.ChatRoom;

import java.util.List;

public record ChatRoomHistoryResult(
        ChatRoom chatRoom,
        List<ChatMessage> messages
) {
}
