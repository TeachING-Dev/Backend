package com.teaching.backend.domain.notification.service;

import com.teaching.backend.domain.notification.entity.Notification;
import com.teaching.backend.domain.notification.enums.NotificationTargetType;
import com.teaching.backend.domain.notification.enums.NotificationType;
import com.teaching.backend.domain.notification.repository.NotificationRepository;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class NotificationReminderCreationService {

    private static final int SHORT_CUT_FIRST_REMINDER_DAYS = 5;
    private static final int SHORT_CUT_REMINDER_INTERVAL_DAYS = 2;
    private static final int DEEP_DIVE_FIRST_REMINDER_DAYS = 10;
    private static final int DEEP_DIVE_REMINDER_INTERVAL_DAYS = 3;
    private static final int MAX_MESSAGE_LENGTH = 60;
    private static final String ELLIPSIS = "...";
    private static final String DEFAULT_TITLE = "티칭맵";

    private final NotificationRepository notificationRepository;
    private final TeachingMapRepository teachingMapRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createReminderIfDue(Long teachingMapId, LocalDateTime now) {
        Optional<TeachingMap> teachingMapOptional = teachingMapRepository
                .findByIdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                        teachingMapId,
                        TeachingMapStatus.IN_PROGRESS
                );

        if (teachingMapOptional.isEmpty()) {
            return false;
        }

        TeachingMap teachingMap = teachingMapOptional.get();
        if (!isReminderDue(teachingMap, now)) {
            return false;
        }

        NotificationType notificationType = NotificationType.fromTeachingMapType(teachingMap.getType());
        if (hasReminderAlreadyCreatedToday(teachingMap, notificationType, now)) {
            return false;
        }

        Notification notification = Notification.createReminder(
                teachingMap.getUser(),
                notificationType,
                buildReminderMessage(notificationType, teachingMap.getTitle()),
                NotificationTargetType.TEACHING_MAP,
                teachingMap.getId()
        );
        notificationRepository.save(notification);
        return true;
    }

    private boolean isReminderDue(TeachingMap teachingMap, LocalDateTime now) {
        TeachingMapType type = teachingMap.getType();
        if (type == null || type == TeachingMapType.ALL || teachingMap.getCreatedAt() == null) {
            return false;
        }

        long elapsedDays = ChronoUnit.DAYS.between(
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
            return DEFAULT_TITLE;
        }

        return title.trim();
    }

    private String fitMessage(String title, Function<String, String> template) {
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
}
