package com.teaching.backend.domain.notification.dto;

import com.teaching.backend.domain.notification.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationReadResponse(
        @Schema(description = "읽음 처리한 알림 ID", example = "1")
        Long notificationId,
        @Schema(description = "읽음 여부", example = "true")
        Boolean isRead
) {

    public static NotificationReadResponse from(Notification notification) {
        return new NotificationReadResponse(
                notification.getId(),
                notification.getIsRead()
        );
    }
}
