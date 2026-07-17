package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.chat.entity.ChatRoom;

import java.util.List;

// 채팅방 목록 조회 결과(현재 페이지 데이터 + 다음 페이지 커서)를 담는 서비스 내부 전달 객체
public record ChatRoomPageResult(
        List<ChatRoom> chatRooms,
        Long nextCursor
) {
}
