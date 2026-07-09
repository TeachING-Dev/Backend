package com.teaching.backend.service;

import com.teaching.backend.entity.ChatRoom;

import java.util.List;

public record ChatRoomPageResult(
        List<ChatRoom> chatRooms,
        Long nextCursor
) {
}
