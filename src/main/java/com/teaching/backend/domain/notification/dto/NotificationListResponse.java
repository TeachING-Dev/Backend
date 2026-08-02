package com.teaching.backend.domain.notification.dto;

import com.teaching.backend.domain.notification.entity.Notification;
import com.teaching.backend.domain.notification.enums.NotificationTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record NotificationListResponse(
        @Schema(description = "알림 ID", example = "1")
        Long notificationId,
        @Schema(description = "알림 유형", example = "SHORT_CUT", allowableValues = {"SHORT_CUT", "DEEP_DIVE"})
        String notificationType,
        @Schema(description = "알림 대상 타입", example = "TEACHING_MAP", allowableValues = {"TEACHING_MAP", "MATERIAL", "FOLDER"})
        String targetType,
        @Schema(description = "알림 대상 ID. 삭제되었거나 접근할 수 없는 티칭맵 대상은 null입니다.", example = "10")
        Long targetId,
        @Schema(description = "알림 대상 제목. 티칭맵 알림이면 티칭맵 제목입니다.", example = "Backend")
        String targetTitle,
        @Schema(description = "알림 제목", example = "Short-cut")
        String title,
        @Schema(description = "알림 메시지", example = "잠시 멈췄던 Backend, Short-Cut으로 빠르게 다시 흐름을 타보세요!")
        String message,
        @Schema(description = "읽음 여부", example = "false")
        Boolean isRead,
        @Schema(description = "알림 생성 시각")
        LocalDateTime createdAt
) {

    public static NotificationListResponse from(Notification notification, String targetTitle) {
        Long targetId = resolveTargetId(notification, targetTitle);

        return new NotificationListResponse(
                notification.getId(),
                notification.getNotificationType() == null ? null : notification.getNotificationType().name(),
                notification.getTargetType() == null ? null : notification.getTargetType().name(),
                targetId,
                targetTitle,
                notification.getTitle(),
                notification.getContent(),
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }

    private static Long resolveTargetId(Notification notification, String targetTitle) {
        if (notification.getTargetType() == NotificationTargetType.TEACHING_MAP && targetTitle == null) {
            return null;
        }

        return notification.getTargetId();
    }
}
