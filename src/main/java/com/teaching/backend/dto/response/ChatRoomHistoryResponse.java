package com.teaching.backend.dto.response;

import java.util.List;

public record ChatRoomHistoryResponse(
        Long chatroomId,
        String title,
        List<MessageResponse> messages
) {
}
