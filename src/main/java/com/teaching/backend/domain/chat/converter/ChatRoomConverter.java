package com.teaching.backend.domain.chat.converter;

import com.teaching.backend.domain.chat.dto.ChatRoomListResponse;
import com.teaching.backend.domain.chat.dto.ChatRoomResponse;
import com.teaching.backend.domain.chat.dto.ChatRoomSummaryResponse;
import com.teaching.backend.domain.chat.entity.ChatRoom;
import com.teaching.backend.domain.chat.service.ChatRoomPageResult;

// ChatRoom 엔티티 및 페이지 결과를 응답 DTO로 변환하는 컨버터
public class ChatRoomConverter {

    private ChatRoomConverter() {
    }

    public static ChatRoomResponse toResponse(ChatRoom chatRoom) {
        return new ChatRoomResponse(
                chatRoom.getId(),
                chatRoom.getTitle(),
                chatRoom.getCreatedAt()
        );
    }

    public static ChatRoomSummaryResponse toSummaryResponse(ChatRoom chatRoom) {
        return new ChatRoomSummaryResponse(
                chatRoom.getId(),
                chatRoom.getTitle(),
                chatRoom.getLastMessageAt()
        );
    }

    public static ChatRoomListResponse toListResponse(ChatRoomPageResult pageResult) {
        return new ChatRoomListResponse(
                pageResult.chatRooms().stream()
                        .map(ChatRoomConverter::toSummaryResponse)
                        .toList(),
                pageResult.nextCursor()
        );
    }
}
