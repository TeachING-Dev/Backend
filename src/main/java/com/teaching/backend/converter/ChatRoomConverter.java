package com.teaching.backend.converter;

import com.teaching.backend.dto.response.ChatRoomListResponse;
import com.teaching.backend.dto.response.ChatRoomResponse;
import com.teaching.backend.dto.response.ChatRoomSummaryResponse;
import com.teaching.backend.entity.ChatRoom;
import com.teaching.backend.service.ChatRoomPageResult;

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
