package com.teaching.backend.domain.chat.dto;

// AI 답변의 근거 자료(출처) 정보를 담는 응답 DTO
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
