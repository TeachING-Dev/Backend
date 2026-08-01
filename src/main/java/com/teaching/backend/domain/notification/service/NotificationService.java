package com.teaching.backend.domain.notification.service;

import com.teaching.backend.domain.notification.dto.NotificationListResponse;
import com.teaching.backend.domain.notification.dto.NotificationReadResponse;
import com.teaching.backend.domain.notification.dto.NotificationSummaryResponse;
import com.teaching.backend.domain.notification.entity.Notification;
import com.teaching.backend.domain.notification.enums.NotificationTargetType;
import com.teaching.backend.domain.notification.enums.NotificationType;
import com.teaching.backend.domain.notification.exception.NotificationErrorCode;
import com.teaching.backend.domain.notification.exception.NotificationException;
import com.teaching.backend.domain.notification.repository.NotificationRepository;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int RECENT_DAYS = 30;
    private static final int SHORT_CUT_FIRST_REMINDER_DAYS = 5;
    private static final int SHORT_CUT_REMINDER_INTERVAL_DAYS = 2;
    private static final int DEEP_DIVE_FIRST_REMINDER_DAYS = 10;
    private static final int DEEP_DIVE_REMINDER_INTERVAL_DAYS = 3;
    private static final int MAX_MESSAGE_LENGTH = 60;
    private static final String ELLIPSIS = "...";

    private final NotificationRepository notificationRepository;
    private final TeachingMapRepository teachingMapRepository;

    public List<NotificationListResponse> getNotifications(Long userId, Integer size) {
        validateUserId(userId);
        validateSize(size);

        List<Notification> notifications = findNotifications(userId, size);
        Map<Long, String> teachingMapTitleById = getTeachingMapTitleById(userId, notifications);

        return notifications.stream()
                .map(notification -> NotificationListResponse.from(
                        notification,
                        resolveTargetTitle(notification, teachingMapTitleById)
                ))
                .toList();
    }

    public NotificationSummaryResponse getNotificationSummary(Long userId) {
        validateUserId(userId);

        long unreadCount = notificationRepository.countRecentUnreadByUserId(userId, recentSince());
        return NotificationSummaryResponse.of(unreadCount);
    }

    @Transactional
    public NotificationReadResponse markAsRead(Long userId, Long notificationId) {
        validateUserId(userId);
        if (notificationId == null || notificationId <= 0) {
            throw new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }

        Notification notification = notificationRepository.findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markAsRead();
        return NotificationReadResponse.from(notification);
    }

    @Transactional
    public int createTeachingMapReminders(LocalDateTime now) {
        LocalDateTime baseNow = now == null ? LocalDateTime.now() : now;
        List<TeachingMap> candidates = teachingMapRepository
                .findAllByStatusAndIsDraftFalseAndDeletedAtIsNullAndCreatedAtLessThanEqual(
                        TeachingMapStatus.IN_PROGRESS,
                        baseNow.minusDays(SHORT_CUT_FIRST_REMINDER_DAYS)
                );

        int createdCount = 0;
        for (TeachingMap teachingMap : candidates) {
            if (!isReminderDue(teachingMap, baseNow)) {
                continue;
            }

            NotificationType notificationType = NotificationType.fromTeachingMapType(teachingMap.getType());
            if (hasReminderAlreadyCreatedToday(teachingMap, notificationType, baseNow)) {
                continue;
            }

            Notification notification = Notification.createReminder(
                    teachingMap.getUser(),
                    notificationType,
                    buildReminderMessage(notificationType, teachingMap.getTitle()),
                    NotificationTargetType.TEACHING_MAP,
                    teachingMap.getId()
            );
            notificationRepository.save(notification);
            createdCount++;
        }

        return createdCount;
    }

    private List<Notification> findNotifications(Long userId, Integer size) {
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;

        return notificationRepository.findRecentByUserId(
                userId,
                recentSince(),
                PageRequest.of(0, pageSize)
        );
    }

    private Map<Long, String> getTeachingMapTitleById(Long userId, List<Notification> notifications) {
        List<Long> teachingMapIds = notifications.stream()
                .filter(notification -> notification.getTargetType() == NotificationTargetType.TEACHING_MAP)
                .map(Notification::getTargetId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (teachingMapIds.isEmpty()) {
            return Map.of();
        }

        return teachingMapRepository.findAllByIdInAndUser_IdAndDeletedAtIsNull(teachingMapIds, userId)
                .stream()
                .collect(Collectors.toMap(
                        TeachingMap::getId,
                        TeachingMap::getTitle,
                        (current, ignored) -> current
                ));
    }

    private String resolveTargetTitle(Notification notification, Map<Long, String> teachingMapTitleById) {
        if (notification.getTargetType() != NotificationTargetType.TEACHING_MAP || notification.getTargetId() == null) {
            return null;
        }

        return teachingMapTitleById.get(notification.getTargetId());
    }

    private boolean isReminderDue(TeachingMap teachingMap, LocalDateTime now) {
        TeachingMapType type = teachingMap.getType();
        if (type == null || type == TeachingMapType.ALL || teachingMap.getCreatedAt() == null) {
            return false;
        }

        long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(
                teachingMap.getCreatedAt().toLocalDate(),
                now.toLocalDate()
        );

        return switch (type) {
            case SHORTCUT -> isDueOnInterval(
                    elapsedDays,
                    SHORT_CUT_FIRST_REMINDER_DAYS,
                    SHORT_CUT_REMINDER_INTERVAL_DAYS
            );
            case DEEPDIVE -> isDueOnInterval(
                    elapsedDays,
                    DEEP_DIVE_FIRST_REMINDER_DAYS,
                    DEEP_DIVE_REMINDER_INTERVAL_DAYS
            );
            case ALL -> false;
        };
    }

    private boolean isDueOnInterval(long elapsedDays, int firstReminderDays, int intervalDays) {
        return elapsedDays >= firstReminderDays && (elapsedDays - firstReminderDays) % intervalDays == 0;
    }

    private boolean hasReminderAlreadyCreatedToday(
            TeachingMap teachingMap,
            NotificationType notificationType,
            LocalDateTime now
    ) {
        return notificationRepository.existsByUser_IdAndTargetTypeAndTargetIdAndNotificationTypeAndCreatedAtGreaterThanEqual(
                teachingMap.getUser().getId(),
                NotificationTargetType.TEACHING_MAP,
                teachingMap.getId(),
                notificationType,
                now.toLocalDate().atStartOfDay()
        );
    }

    private String buildReminderMessage(NotificationType notificationType, String teachingMapTitle) {
        String safeTitle = normalizeTitle(teachingMapTitle);
        return switch (notificationType) {
            case SHORT_CUT -> fitMessage(
                    safeTitle,
                    title -> "잠시 멈췄던 " + title + ", Short-Cut으로 빠르게 다시 흐름을 타보세요!"
            );
            case DEEP_DIVE -> fitMessage(
                    safeTitle,
                    title -> title + " 학습이 잠시 멈췄네요. 다시 꼼꼼하게 파고들어 볼까요?"
            );
        };
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "티칭맵";
        }

        return title.trim();
    }

    private String fitMessage(String title, java.util.function.Function<String, String> template) {
        String message = template.apply(title);
        if (message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }

        int overflow = message.length() - MAX_MESSAGE_LENGTH;
        int titleLength = Math.max(1, title.length() - overflow - ELLIPSIS.length());
        String shortenedTitle = title.substring(0, Math.min(title.length(), titleLength)) + ELLIPSIS;
        String shortenedMessage = template.apply(shortenedTitle);

        if (shortenedMessage.length() <= MAX_MESSAGE_LENGTH) {
            return shortenedMessage;
        }

        return shortenedMessage.substring(0, MAX_MESSAGE_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    private void validateSize(Integer size) {
        if (size != null && (size <= 0 || size > MAX_PAGE_SIZE)) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private LocalDateTime recentSince() {
        return LocalDateTime.now().minusDays(RECENT_DAYS);
    }
}
