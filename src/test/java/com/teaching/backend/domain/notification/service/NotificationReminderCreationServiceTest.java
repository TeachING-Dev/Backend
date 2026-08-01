package com.teaching.backend.domain.notification.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.notification.entity.Notification;
import com.teaching.backend.domain.notification.enums.NotificationTargetType;
import com.teaching.backend.domain.notification.enums.NotificationType;
import com.teaching.backend.domain.notification.repository.NotificationRepository;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class NotificationReminderCreationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TEACHING_MAP_ID = 1000L;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private TeachingMapRepository teachingMapRepository;

    @InjectMocks
    private NotificationReminderCreationService reminderCreationService;

    @Test
    void createReminderIfDueCreatesShortcutReminderOnFifthDay() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        TeachingMap teachingMap = teachingMap(TeachingMapType.SHORTCUT, now.minusDays(5), "Backend");
        when(teachingMapRepository.findByIdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                TEACHING_MAP_ID,
                TeachingMapStatus.IN_PROGRESS
        )).thenReturn(Optional.of(teachingMap));
        when(notificationRepository.existsByUser_IdAndTargetTypeAndTargetIdAndNotificationTypeAndCreatedAtGreaterThanEqual(
                USER_ID,
                NotificationTargetType.TEACHING_MAP,
                TEACHING_MAP_ID,
                NotificationType.SHORT_CUT,
                now.toLocalDate().atStartOfDay()
        )).thenReturn(false);

        boolean result = reminderCreationService.createReminderIfDue(TEACHING_MAP_ID, now);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(result).isTrue();
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.SHORT_CUT);
        assertThat(saved.getTitle()).isEqualTo("Short-Cut");
        assertThat(saved.getContent()).contains("Backend");
        assertThat(saved.getContent()).hasSizeLessThanOrEqualTo(60);
        assertThat(saved.getTargetType()).isEqualTo(NotificationTargetType.TEACHING_MAP);
        assertThat(saved.getTargetId()).isEqualTo(TEACHING_MAP_ID);
    }

    @Test
    void createReminderIfDueSkipsShortcutBeforeFifthDay() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        TeachingMap teachingMap = teachingMap(TeachingMapType.SHORTCUT, now.minusDays(4), "Backend");
        when(teachingMapRepository.findByIdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                TEACHING_MAP_ID,
                TeachingMapStatus.IN_PROGRESS
        )).thenReturn(Optional.of(teachingMap));

        boolean result = reminderCreationService.createReminderIfDue(TEACHING_MAP_ID, now);

        assertThat(result).isFalse();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void createReminderIfDueCreatesDeepDiveReminderOnTenthDay() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        TeachingMap teachingMap = teachingMap(TeachingMapType.DEEPDIVE, now.minusDays(10), "Deep Backend");
        when(teachingMapRepository.findByIdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                TEACHING_MAP_ID,
                TeachingMapStatus.IN_PROGRESS
        )).thenReturn(Optional.of(teachingMap));
        when(notificationRepository.existsByUser_IdAndTargetTypeAndTargetIdAndNotificationTypeAndCreatedAtGreaterThanEqual(
                USER_ID,
                NotificationTargetType.TEACHING_MAP,
                TEACHING_MAP_ID,
                NotificationType.DEEP_DIVE,
                now.toLocalDate().atStartOfDay()
        )).thenReturn(false);

        boolean result = reminderCreationService.createReminderIfDue(TEACHING_MAP_ID, now);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(result).isTrue();
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.DEEP_DIVE);
        assertThat(saved.getTitle()).isEqualTo("Deep-Dive");
        assertThat(saved.getContent()).contains("Deep Backend");
        assertThat(saved.getContent()).hasSizeLessThanOrEqualTo(60);
    }

    @Test
    void createReminderIfDueSkipsAlreadyCreatedReminderForToday() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        TeachingMap teachingMap = teachingMap(TeachingMapType.SHORTCUT, now.minusDays(7), "Backend");
        when(teachingMapRepository.findByIdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                TEACHING_MAP_ID,
                TeachingMapStatus.IN_PROGRESS
        )).thenReturn(Optional.of(teachingMap));
        when(notificationRepository.existsByUser_IdAndTargetTypeAndTargetIdAndNotificationTypeAndCreatedAtGreaterThanEqual(
                USER_ID,
                NotificationTargetType.TEACHING_MAP,
                TEACHING_MAP_ID,
                NotificationType.SHORT_CUT,
                now.toLocalDate().atStartOfDay()
        )).thenReturn(true);

        boolean result = reminderCreationService.createReminderIfDue(TEACHING_MAP_ID, now);

        assertThat(result).isFalse();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void createReminderIfDueShortensLongTitleMessageToSixtyCharacters() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        TeachingMap teachingMap = teachingMap(
                TeachingMapType.SHORTCUT,
                now.minusDays(5),
                "VeryLongTeachingMapTitleVeryLongTeachingMapTitleVeryLongTeachingMapTitle"
        );
        when(teachingMapRepository.findByIdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                TEACHING_MAP_ID,
                TeachingMapStatus.IN_PROGRESS
        )).thenReturn(Optional.of(teachingMap));

        reminderCreationService.createReminderIfDue(TEACHING_MAP_ID, now);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getContent()).hasSizeLessThanOrEqualTo(60);
    }

    private TeachingMap teachingMap(TeachingMapType type, LocalDateTime createdAt, String title) {
        User user = User.create("user@example.com", "user", null, null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
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
        ReflectionTestUtils.setField(teachingMap, "id", TEACHING_MAP_ID);
        ReflectionTestUtils.setField(teachingMap, "createdAt", createdAt);
        return teachingMap;
    }
}
