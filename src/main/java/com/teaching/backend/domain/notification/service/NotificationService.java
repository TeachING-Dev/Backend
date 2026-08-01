package com.teaching.backend.domain.notification.service;

import com.teaching.backend.domain.notification.dto.NotificationListResponse;
import com.teaching.backend.domain.notification.dto.NotificationReadResponse;
import com.teaching.backend.domain.notification.dto.NotificationSummaryResponse;
import com.teaching.backend.domain.notification.entity.Notification;
import com.teaching.backend.domain.notification.enums.NotificationTargetType;
import com.teaching.backend.domain.notification.exception.NotificationErrorCode;
import com.teaching.backend.domain.notification.exception.NotificationException;
import com.teaching.backend.domain.notification.repository.NotificationRepository;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int RECENT_DAYS = 30;
    private static final int REMINDER_BATCH_SIZE = 100;
    private static final int REMINDER_LOOKBACK_DAYS = 5;
    static final ZoneId REMINDER_ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;
    private final TeachingMapRepository teachingMapRepository;
    private final NotificationReminderCreationService reminderCreationService;

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

    public int createTeachingMapReminders(LocalDateTime now) {
        LocalDateTime baseNow = now == null ? LocalDateTime.now(REMINDER_ZONE) : now;
        int createdCount = 0;
        int failedCount = 0;
        int pageNumber = 0;

        Page<TeachingMap> page;
        do {
            page = teachingMapRepository.findAllByStatusAndIsDraftFalseAndDeletedAtIsNullAndCreatedAtLessThanEqual(
                    TeachingMapStatus.IN_PROGRESS,
                    baseNow.minusDays(REMINDER_LOOKBACK_DAYS),
                    PageRequest.of(pageNumber, REMINDER_BATCH_SIZE, reminderCandidateSort())
            );

            for (TeachingMap teachingMap : page.getContent()) {
                try {
                    if (reminderCreationService.createReminderIfDue(teachingMap.getId(), baseNow)) {
                        createdCount++;
                    }
                } catch (RuntimeException exception) {
                    failedCount++;
                    log.error(
                            "Failed to create teaching map reminder. teachingMapId={}, reason={}",
                            teachingMap.getId(),
                            exception.getClass().getSimpleName(),
                            exception
                    );
                }
            }

            pageNumber++;
        } while (page.hasNext());

        log.info("Teaching map reminder batch finished. createdCount={}, failedCount={}", createdCount, failedCount);
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
        return LocalDateTime.now(REMINDER_ZONE).minusDays(RECENT_DAYS);
    }

    private Sort reminderCandidateSort() {
        return Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id")
        );
    }
}
