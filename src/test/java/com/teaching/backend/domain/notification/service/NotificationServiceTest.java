package com.teaching.backend.domain.notification.service;

import com.teaching.backend.domain.notification.dto.NotificationListResponse;
import com.teaching.backend.domain.notification.entity.Notification;
import com.teaching.backend.domain.notification.enums.NotificationTargetType;
import com.teaching.backend.domain.notification.repository.NotificationRepository;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

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
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void getNotificationsUsesDefaultSizeWhenSizeIsNull() {
        Notification first = notification(101L, USER_ID, "Title 1", "Content 1", NotificationTargetType.MATERIAL, false, createdAt(1));
        Notification second = notification(102L, USER_ID, "Title 2", "Content 2", NotificationTargetType.TEACHING_MAP, true, createdAt(2));
        when(notificationRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        List<NotificationListResponse> result = notificationService.getNotifications(USER_ID, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findAllByUser_Id(eq(USER_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(result).extracting(NotificationListResponse::notificationId)
                .containsExactly(101L, 102L);
        verify(notificationRepository, never()).findAllByUser_Id(eq(USER_ID), any(Sort.class));
    }

    @Test
    void getNotificationsUsesPageableWhenSizeExists() {
        Notification notification = notification(101L, USER_ID, "Title", "Content", NotificationTargetType.FOLDER, false, createdAt(1));
        when(notificationRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));

        List<NotificationListResponse> result = notificationService.getNotifications(USER_ID, 1);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findAllByUser_Id(eq(USER_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(result).hasSize(1);
    }

    @Test
    void getNotificationsReturnsEmptyListWhenNoNotificationExists() {
        when(notificationRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<NotificationListResponse> result = notificationService.getNotifications(USER_ID, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getNotificationsPassesCurrentUserIdToRepository() {
        when(notificationRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        notificationService.getNotifications(USER_ID, null);

        verify(notificationRepository).findAllByUser_Id(eq(USER_ID), any(Pageable.class));
        verify(notificationRepository, never()).findAllByUser_Id(eq(OTHER_USER_ID), any(Pageable.class));
    }

    @Test
    void getNotificationsMapsDtoFields() {
        LocalDateTime createdAt = createdAt(1);
        Notification notification = notification(
                101L,
                USER_ID,
                "Title",
                "Content",
                NotificationTargetType.TEACHING_MAP,
                true,
                createdAt
        );
        when(notificationRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));

        NotificationListResponse result = notificationService.getNotifications(USER_ID, null).get(0);

        assertThat(result.notificationId()).isEqualTo(101L);
        assertThat(result.title()).isEqualTo("Title");
        assertThat(result.message()).isEqualTo("Content");
        assertThat(result.isRead()).isTrue();
        assertThat(result.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void getNotificationsRejectsZeroSize() {
        assertBadRequestThrown(() -> notificationService.getNotifications(USER_ID, 0));
    }

    @Test
    void getNotificationsRejectsNegativeSize() {
        assertBadRequestThrown(() -> notificationService.getNotifications(USER_ID, -1));
    }

    @Test
    void getNotificationsAllowsMaxSize() {
        when(notificationRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        notificationService.getNotifications(USER_ID, 100);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findAllByUser_Id(eq(USER_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void getNotificationsRejectsSizeGreaterThanMax() {
        assertBadRequestThrown(() -> notificationService.getNotifications(USER_ID, 101));
    }

    @Test
    void getNotificationsAppliesRecentSort() {
        when(notificationRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        notificationService.getNotifications(USER_ID, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findAllByUser_Id(eq(USER_ID), pageableCaptor.capture());
        Sort sort = pageableCaptor.getValue().getSort();
        assertThat(sort.getOrderFor("createdAt")).isNotNull();
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id")).isNotNull();
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
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
            boolean isRead,
            LocalDateTime createdAt
    ) {
        Notification notification = Notification.createWithTarget(
                user(userId),
                title,
                content,
                targetType,
                1000L
        );
        ReflectionTestUtils.setField(notification, "id", notificationId);
        ReflectionTestUtils.setField(notification, "isRead", isRead);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        return notification;
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
