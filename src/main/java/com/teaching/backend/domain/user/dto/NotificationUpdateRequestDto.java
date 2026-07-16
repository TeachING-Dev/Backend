package com.teaching.backend.domain.user.dto;

/**
 * [PATCH] /users/me/notifications 요청 바디.
 * pushEnabled 는 필수. 없거나 형식이 틀리면 NOTIFICATION_INVALID.
 */
public record NotificationUpdateRequestDto(
        Boolean pushEnabled
) {
}
