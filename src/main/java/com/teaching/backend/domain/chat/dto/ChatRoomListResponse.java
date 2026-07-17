package com.teaching.backend.domain.chat.dto;

import java.util.List;

// 채팅방 목록 조회 응답 DTO (커서 기반 페이지네이션 포함)
public record ChatRoomListResponse(
        List<ChatRoomSummaryResponse> chatrooms,
        Long nextCursor
) {
}
