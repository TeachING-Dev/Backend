package com.teaching.backend.dto.response;

import java.util.List;

public record ChatRoomListResponse(
        List<ChatRoomSummaryResponse> chatrooms,
        Long nextCursor
) {
}
