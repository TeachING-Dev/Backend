package com.teaching.backend.domain.notification.dto;

import com.teaching.backend.domain.notification.entity.Notification;

import java.time.LocalDateTime;

public record NotificationListResponse(
        Long notificationId,
        String title,
        String message,
        Boolean isRead,
        LocalDateTime createdAt
) {

    public static NotificationListResponse from(Notification notification) {
        return new NotificationListResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }
}
