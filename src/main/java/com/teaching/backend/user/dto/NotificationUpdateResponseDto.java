package com.teaching.backend.user.dto;

/**
 * [PATCH] /users/me/notifications 응답 result.
 */
public record NotificationUpdateResponseDto(
        Boolean pushEnabled
) {

    public static NotificationUpdateResponseDto of(Boolean pushEnabled) {
        return new NotificationUpdateResponseDto(pushEnabled);
    }
}
