package com.teaching.backend.domain.support.dto;

/**
 * [GET] /support/contacts 응답 result.
 */
public record SupportContactResponseDto(
        String kakaoChannelUrl,
        String email
) {
}
