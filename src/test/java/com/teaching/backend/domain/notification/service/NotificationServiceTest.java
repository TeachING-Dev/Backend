package com.teaching.backend.domain.notification.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.notification.dto.NotificationListResponse;
import com.teaching.backend.domain.notification.dto.NotificationReadResponse;
import com.teaching.backend.domain.notification.dto.NotificationSummaryResponse;
import com.teaching.backend.domain.notification.entity.Notification;
import com.teaching.backend.domain.notification.enums.NotificationTargetType;
import com.teaching.backend.domain.notification.exception.NotificationErrorCode;
import com.teaching.backend.domain.notification.repository.NotificationRepository;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TEACHING_MAP_ID = 1000L;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private TeachingMapRepository teachingMapRepository;

    @Mock
    private NotificationReminderCreationService reminderCreationService;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void getNotificationsUsesDefaultSizeAndRecentPolicyWhenSizeIsNull() {
        when(notificationRepository.findRecentByUserId(eq(USER_ID), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        List<NotificationListResponse> result = notificationService.getNotifications(USER_ID, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findRecentByUserId(eq(USER_ID), any(LocalDateTime.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(result).isEmpty();
    }

    @Test
    void getNotificationsUsesRequestedSizeForDropdown() {
        when(notificationRepository.findRecentByUserId(eq(USER_ID), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        notificationService.getNotifications(USER_ID, 5);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findRecentByUserId(eq(USER_ID), any(LocalDateTime.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void getNotificationsMapsTeachingMapTargetInformation() {
        Notification notification = notification(
                101L,
                USER_ID,
                "Short-cut",
                "잠시 멈췄던 Backend, Short-Cut으로 빠르게 다시 흐름을 타보세요!",
                NotificationTargetType.TEACHING_MAP,
                TEACHING_MAP_ID,
                false,
                createdAt(1)
        );
        TeachingMap teachingMap = teachingMap(TEACHING_MAP_ID, USER_ID, "Backend");
        when(notificationRepository.findRecentByUserId(eq(USER_ID), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(notification));
        when(teachingMapRepository.findAllByIdInAndUser_IdAndDeletedAtIsNull(List.of(TEACHING_MAP_ID), USER_ID))
                .thenReturn(List.of(teachingMap));

        NotificationListResponse result = notificationService.getNotifications(USER_ID, 5).get(0);

        assertThat(result.notificationId()).isEqualTo(101L);
        assertThat(result.targetType()).isEqualTo("TEACHING_MAP");
        assertThat(result.targetId()).isEqualTo(TEACHING_MAP_ID);
        assertThat(result.targetTitle()).isEqualTo("Backend");
        assertThat(result.title()).isEqualTo("Short-cut");
        assertThat(result.message()).contains("Backend");
        assertThat(result.isRead()).isFalse();
        assertThat(result.createdAt()).isEqualTo(createdAt(1));
    }

    @Test
    void getNotificationsDoesNotExposeMissingTeachingMapTargetId() {
        Notification notification = notification(
                101L,
                USER_ID,
                "Deep-dive",
                "학습이 잠시 멈췄네요.",
                NotificationTargetType.TEACHING_MAP,
                TEACHING_MAP_ID,
                false,
                createdAt(1)
        );
        when(notificationRepository.findRecentByUserId(eq(USER_ID), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(notification));
        when(teachingMapRepository.findAllByIdInAndUser_IdAndDeletedAtIsNull(List.of(TEACHING_MAP_ID), USER_ID))
                .thenReturn(List.of());

        NotificationListResponse result = notificationService.getNotifications(USER_ID, 5).get(0);

        assertThat(result.targetType()).isEqualTo("TEACHING_MAP");
        assertThat(result.targetId()).isNull();
        assertThat(result.targetTitle()).isNull();
    }

    @Test
    void getNotificationsRejectsInvalidSize() {
        assertBadRequestThrown(() -> notificationService.getNotifications(USER_ID, 0));
        assertBadRequestThrown(() -> notificationService.getNotifications(USER_ID, 101));
    }

    @Test
    void getNotificationSummaryReturnsUnreadCountForRecentNotifications() {
        when(notificationRepository.countRecentUnreadByUserId(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(3L);

        NotificationSummaryResponse result = notificationService.getNotificationSummary(USER_ID);

        assertThat(result.hasUnread()).isTrue();
        assertThat(result.unreadCount()).isEqualTo(3L);
    }

    @Test
    void markAsReadUpdatesCurrentUsersNotification() {
        Notification notification = notification(
                101L,
                USER_ID,
                "Title",
                "Content",
                NotificationTargetType.MATERIAL,
                2000L,
                false,
                createdAt(1)
        );
        when(notificationRepository.findByIdAndUser_Id(101L, USER_ID)).thenReturn(Optional.of(notification));

        NotificationReadResponse result = notificationService.markAsRead(USER_ID, 101L);

        assertThat(result.notificationId()).isEqualTo(101L);
        assertThat(result.isRead()).isTrue();
        assertThat(notification.getIsRead()).isTrue();
    }

    @Test
    void markAsReadIsIdempotentWhenNotificationAlreadyRead() {
        Notification notification = notification(
                101L,
                USER_ID,
                "Title",
                "Content",
                NotificationTargetType.MATERIAL,
                2000L,
                true,
                createdAt(1)
        );
        when(notificationRepository.findByIdAndUser_Id(101L, USER_ID)).thenReturn(Optional.of(notification));

        NotificationReadResponse result = notificationService.markAsRead(USER_ID, 101L);

        assertThat(result.isRead()).isTrue();
    }

    @Test
    void markAsReadRejectsOtherUsersNotification() {
        when(notificationRepository.findByIdAndUser_Id(101L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(USER_ID, 101L))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        verify(notificationRepository).findByIdAndUser_Id(101L, USER_ID);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void createTeachingMapRemindersCreatesReminderForDueCandidate() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        TeachingMap teachingMap = teachingMap(TEACHING_MAP_ID, USER_ID, "Backend", TeachingMapType.SHORTCUT, now.minusDays(5));
        when(teachingMapRepository.findAllByStatusAndIsDraftFalseAndDeletedAtIsNullAndCreatedAtLessThanEqual(
                eq(TeachingMapStatus.IN_PROGRESS),
                eq(now.minusDays(5)),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(teachingMap)));
        when(reminderCreationService.createReminderIfDue(TEACHING_MAP_ID, now)).thenReturn(true);

        int result = notificationService.createTeachingMapReminders(now);

        assertThat(result).isEqualTo(1);
        verify(reminderCreationService).createReminderIfDue(TEACHING_MAP_ID, now);
    }

    @Test
    void createTeachingMapRemindersReturnsZeroWhenNoCandidateExists() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        when(teachingMapRepository.findAllByStatusAndIsDraftFalseAndDeletedAtIsNullAndCreatedAtLessThanEqual(
                eq(TeachingMapStatus.IN_PROGRESS),
                eq(now.minusDays(5)),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        int result = notificationService.createTeachingMapReminders(now);

        assertThat(result).isZero();
        verify(reminderCreationService, never()).createReminderIfDue(any(), eq(now));
    }

    @Test
    void createTeachingMapRemindersContinuesWhenOneCandidateFails() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        TeachingMap first = teachingMap(1001L, USER_ID, "First", TeachingMapType.SHORTCUT, now.minusDays(5));
        TeachingMap second = teachingMap(1002L, USER_ID, "Second", TeachingMapType.SHORTCUT, now.minusDays(5));
        TeachingMap third = teachingMap(1003L, USER_ID, "Third", TeachingMapType.SHORTCUT, now.minusDays(5));
        when(teachingMapRepository.findAllByStatusAndIsDraftFalseAndDeletedAtIsNullAndCreatedAtLessThanEqual(
                eq(TeachingMapStatus.IN_PROGRESS),
                eq(now.minusDays(5)),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(first, second, third)));
        when(reminderCreationService.createReminderIfDue(1001L, now)).thenReturn(true);
        when(reminderCreationService.createReminderIfDue(1002L, now)).thenThrow(new IllegalStateException("boom"));
        when(reminderCreationService.createReminderIfDue(1003L, now)).thenReturn(true);

        int result = notificationService.createTeachingMapReminders(now);

        assertThat(result).isEqualTo(2);
        verify(reminderCreationService).createReminderIfDue(1001L, now);
        verify(reminderCreationService).createReminderIfDue(1002L, now);
        verify(reminderCreationService).createReminderIfDue(1003L, now);
    }

    @Test
    void createTeachingMapRemindersProcessesNextBatch() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        List<TeachingMap> firstBatch = LongStream.rangeClosed(1, 100)
                .mapToObj(id -> teachingMap(id, USER_ID, "Map " + id, TeachingMapType.SHORTCUT, now.minusDays(5)))
                .toList();
        TeachingMap last = teachingMap(101L, USER_ID, "Map 101", TeachingMapType.SHORTCUT, now.minusDays(5));
        when(teachingMapRepository.findAllByStatusAndIsDraftFalseAndDeletedAtIsNullAndCreatedAtLessThanEqual(
                eq(TeachingMapStatus.IN_PROGRESS),
                eq(now.minusDays(5)),
                any(Pageable.class)
        )).thenReturn(
                new PageImpl<>(firstBatch, org.springframework.data.domain.PageRequest.of(0, 100), 101),
                new PageImpl<>(List.of(last), org.springframework.data.domain.PageRequest.of(1, 100), 101)
        );
        when(reminderCreationService.createReminderIfDue(any(), eq(now))).thenReturn(true);

        int result = notificationService.createTeachingMapReminders(now);

        assertThat(result).isEqualTo(101);
        verify(reminderCreationService).createReminderIfDue(1L, now);
        verify(reminderCreationService).createReminderIfDue(101L, now);
    }

    @Test
    void createTeachingMapRemindersUsesKstWhenNowIsNull() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            when(teachingMapRepository.findAllByStatusAndIsDraftFalseAndDeletedAtIsNullAndCreatedAtLessThanEqual(
                    eq(TeachingMapStatus.IN_PROGRESS),
                    any(LocalDateTime.class),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of()));

            notificationService.createTeachingMapReminders(null);

            ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(teachingMapRepository).findAllByStatusAndIsDraftFalseAndDeletedAtIsNullAndCreatedAtLessThanEqual(
                    eq(TeachingMapStatus.IN_PROGRESS),
                    thresholdCaptor.capture(),
                    any(Pageable.class)
            );
            assertThat(thresholdCaptor.getValue().toLocalDate())
                    .isEqualTo(LocalDateTime.now(NotificationService.REMINDER_ZONE).minusDays(5).toLocalDate());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private void assertBadRequestThrown(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.BAD_REQUEST);
    }

    private Notification notification(
            Long notificationId,
            Long userId,
            String title,
            String content,
            NotificationTargetType targetType,
            Long targetId,
            boolean isRead,
            LocalDateTime createdAt
    ) {
        Notification notification = Notification.createWithTarget(
                user(userId),
                title,
                content,
                targetType,
                targetId
        );
        ReflectionTestUtils.setField(notification, "id", notificationId);
        ReflectionTestUtils.setField(notification, "isRead", isRead);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        return notification;
    }

    private TeachingMap teachingMap(Long teachingMapId, Long userId, String title) {
        return teachingMap(teachingMapId, userId, title, TeachingMapType.SHORTCUT, createdAt(1));
    }

    private TeachingMap teachingMap(Long teachingMapId, Long userId, String title, TeachingMapType type, LocalDateTime createdAt) {
        User user = user(userId);
        Folder folder = Folder.create(user, "Folder");
        TeachingMap teachingMap = TeachingMap.create(
                folder,
                user,
                title,
                "Description",
                5,
                type,
                false
        );
        ReflectionTestUtils.setField(teachingMap, "id", teachingMapId);
        ReflectionTestUtils.setField(teachingMap, "createdAt", createdAt);
        return teachingMap;
    }

    private User user(Long userId) {
        User user = User.create("user" + userId + "@example.com", "user" + userId, null, null, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private LocalDateTime createdAt(int day) {
        return LocalDateTime.of(2026, 7, day, 10, 0);
    }
}
