package com.teaching.backend.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationSummaryResponse(
        @Schema(description = "최근 30일 내 읽지 않은 알림 존재 여부", example = "true")
        Boolean hasUnread,
        @Schema(description = "최근 30일 내 읽지 않은 알림 수", example = "3")
        Long unreadCount
) {

    public static NotificationSummaryResponse of(long unreadCount) {
        return new NotificationSummaryResponse(unreadCount > 0, unreadCount);
    }
}
