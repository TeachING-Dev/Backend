package com.teaching.backend.dto.response;

public record ChatSourceResponse(
        Long chatsourceId,
        Long materialId,
        String materialTitle,
        String folderName,
        String url,
        String citedText,
        String position
) {
}
