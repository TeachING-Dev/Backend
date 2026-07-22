package com.teaching.backend.domain.teachingmap.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.teachingmap.dto.TeachingMapListResponse;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeachingMapServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private TeachingMapRepository teachingMapRepository;

    @InjectMocks
    private TeachingMapService teachingMapService;

    @Test
    void getTeachingMapsReturnsAllStatusesWhenStatusIsNull() {
        TeachingMap inProgress = teachingMap(101L, USER_ID, "In Progress", TeachingMapStatus.IN_PROGRESS, TeachingMapType.SHORTCUT, createdAt(1));
        TeachingMap finished = teachingMap(102L, USER_ID, "Finished", TeachingMapStatus.FINISHED, TeachingMapType.DEEPDIVE, createdAt(2));
        when(teachingMapRepository.findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of(inProgress, finished));

        List<TeachingMapListResponse> result = teachingMapService.getTeachingMaps(USER_ID, null, null);

        assertThat(result).extracting(TeachingMapListResponse::teachingMapId)
                .containsExactly(101L, 102L);
        verify(teachingMapRepository).findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), any(Sort.class));
        verify(teachingMapRepository, never()).findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                any(TeachingMapStatus.class),
                any(Sort.class)
        );
    }

    @Test
    void getTeachingMapsFiltersByInProgressStatus() {
        TeachingMap inProgress = teachingMap(101L, USER_ID, "In Progress", TeachingMapStatus.IN_PROGRESS, TeachingMapType.SHORTCUT, createdAt(1));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Sort.class)
        )).thenReturn(List.of(inProgress));

        List<TeachingMapListResponse> result = teachingMapService.getTeachingMaps(USER_ID, TeachingMapStatus.IN_PROGRESS, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void getTeachingMapsUsesPageableWhenSizeExists() {
        TeachingMap teachingMap = teachingMap(101L, USER_ID, "Map", TeachingMapStatus.IN_PROGRESS, TeachingMapType.SHORTCUT, createdAt(1));
        when(teachingMapRepository.findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(teachingMap)));

        List<TeachingMapListResponse> result = teachingMapService.getTeachingMaps(USER_ID, null, 5);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(teachingMapRepository).findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(result).hasSize(1);
    }

    @Test
    void getTeachingMapsAppliesRecentSort() {
        when(teachingMapRepository.findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of());

        teachingMapService.getTeachingMaps(USER_ID, null, null);

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(teachingMapRepository).findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), sortCaptor.capture());
        Sort sort = sortCaptor.getValue();
        assertThat(sort.getOrderFor("createdAt")).isNotNull();
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id")).isNotNull();
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getTeachingMapsReturnsEmptyListWhenNoResultExists() {
        when(teachingMapRepository.findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of());

        List<TeachingMapListResponse> result = teachingMapService.getTeachingMaps(USER_ID, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getTeachingMapsPassesCurrentUserIdToRepository() {
        when(teachingMapRepository.findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of());

        teachingMapService.getTeachingMaps(USER_ID, null, null);

        verify(teachingMapRepository).findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), any(Sort.class));
        verify(teachingMapRepository, never()).findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(OTHER_USER_ID), any(Sort.class));
    }

    @Test
    void getTeachingMapsUsesDeletedAtIsNullAndDraftFalseRepositoryMethod() {
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.FINISHED),
                any(Sort.class)
        )).thenReturn(List.of());

        teachingMapService.getTeachingMaps(USER_ID, TeachingMapStatus.FINISHED, null);

        verify(teachingMapRepository).findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.FINISHED),
                any(Sort.class)
        );
    }

    @Test
    void getTeachingMapsRejectsZeroSize() {
        assertBadRequestThrown(() -> teachingMapService.getTeachingMaps(USER_ID, null, 0));
    }

    @Test
    void getTeachingMapsRejectsNegativeSize() {
        assertBadRequestThrown(() -> teachingMapService.getTeachingMaps(USER_ID, null, -1));
    }

    @Test
    void getTeachingMapsAllowsNullType() {
        TeachingMap teachingMap = teachingMap(101L, USER_ID, "Map", TeachingMapStatus.IN_PROGRESS, TeachingMapType.SHORTCUT, createdAt(1));
        ReflectionTestUtils.setField(teachingMap, "type", null);
        when(teachingMapRepository.findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of(teachingMap));

        List<TeachingMapListResponse> result = teachingMapService.getTeachingMaps(USER_ID, null, null);

        assertThat(result.get(0).type()).isNull();
    }

    @Test
    void getTeachingMapsAllowsNullStatus() {
        TeachingMap teachingMap = teachingMap(101L, USER_ID, "Map", TeachingMapStatus.IN_PROGRESS, TeachingMapType.SHORTCUT, createdAt(1));
        ReflectionTestUtils.setField(teachingMap, "status", null);
        when(teachingMapRepository.findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of(teachingMap));

        assertThatCode(() -> teachingMapService.getTeachingMaps(USER_ID, null, null))
                .doesNotThrowAnyException();
        assertThat(teachingMapService.getTeachingMaps(USER_ID, null, null).get(0).status()).isNull();
    }

    @Test
    void getTeachingMapsMapsResponseFields() {
        LocalDateTime createdAt = createdAt(1);
        TeachingMap teachingMap = teachingMap(101L, USER_ID, "Map", TeachingMapStatus.IN_PROGRESS, TeachingMapType.DEEPDIVE, createdAt);
        when(teachingMapRepository.findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of(teachingMap));

        TeachingMapListResponse result = teachingMapService.getTeachingMaps(USER_ID, null, null).get(0);

        assertThat(result.teachingMapId()).isEqualTo(101L);
        assertThat(result.title()).isEqualTo("Map");
        assertThat(result.description()).isEqualTo("Description");
        assertThat(result.type()).isEqualTo("DEEPDIVE");
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.currentSteps()).isEqualTo(0);
        assertThat(result.totalSteps()).isEqualTo(5);
        assertThat(result.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void getTeachingMapsKeepsHomeDashboardRepositoryMethodAvailable() throws NoSuchMethodException {
        TeachingMapRepository.class.getMethod(
                "findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull",
                Long.class,
                TeachingMapStatus.class,
                Pageable.class
        );
    }

    @Test
    void getTeachingMapsAppliesPageableWithStatus() {
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        teachingMapService.getTeachingMaps(USER_ID, TeachingMapStatus.IN_PROGRESS, 3);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(teachingMapRepository).findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    private void assertBadRequestThrown(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.BAD_REQUEST);
    }

    private TeachingMap teachingMap(
            Long teachingMapId,
            Long userId,
            String title,
            TeachingMapStatus status,
            TeachingMapType type,
            LocalDateTime createdAt
    ) {
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
        ReflectionTestUtils.setField(teachingMap, "status", status);
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
