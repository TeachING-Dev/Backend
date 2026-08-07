package com.teaching.backend.domain.home.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.home.dto.HomeDashboardResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapPlatformProjection;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapStepRepository;
import com.teaching.backend.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private TeachingMapRepository teachingMapRepository;

    @Mock
    private TeachingMapStepRepository teachingMapStepRepository;

    @InjectMocks
    private HomeService homeService;

    @BeforeEach
    void setUp() {
        lenient().when(teachingMapStepRepository.findActivePlatformTypesByTeachingMapIdIn(any(), eq(USER_ID)))
                .thenReturn(List.of());
    }

    @Test
    void getDashboardMapsFiveRecentMaterialsAndThreeActiveTeachingMaps() {
        List<Material> materials = List.of(
                material(101L, USER_ID, "Material 1", PlatformType.YOUTUBE, AiStatus.COMPLETED, createdAt(1)),
                material(102L, USER_ID, "Material 2", PlatformType.BLOG, AiStatus.COMPLETED, createdAt(2)),
                material(103L, USER_ID, "Material 3", PlatformType.PDF, AiStatus.COMPLETED, createdAt(3)),
                material(104L, USER_ID, "Material 4", PlatformType.WEB, AiStatus.COMPLETED, createdAt(4)),
                material(105L, USER_ID, "Material 5", PlatformType.NOTION, AiStatus.COMPLETED, createdAt(5))
        );
        List<TeachingMap> teachingMaps = List.of(
                teachingMap(201L, USER_ID, "Map 1", TeachingMapType.SHORTCUT, createdAt(1)),
                teachingMap(202L, USER_ID, "Map 2", TeachingMapType.DEEPDIVE, createdAt(2)),
                teachingMap(203L, USER_ID, "Map 3", TeachingMapType.SHORTCUT, createdAt(3))
        );
        when(materialRepository.findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(materials));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(teachingMaps));

        HomeDashboardResponse result = homeService.getDashboard(USER_ID);

        assertThat(result.recentMaterials()).hasSize(5);
        assertThat(result.activeTeachingMaps()).hasSize(3);
        assertThat(result.recentMaterials().get(0).materialId()).isEqualTo(101L);
        assertThat(result.recentMaterials().get(0).folderId()).isEqualTo(FOLDER_ID);
        assertThat(result.activeTeachingMaps().get(0).teachingMapId()).isEqualTo(201L);
    }

    @Test
    void getDashboardReturnsEmptyListsWhenNoDataExists() {
        when(materialRepository.findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        HomeDashboardResponse result = homeService.getDashboard(USER_ID);

        assertThat(result.recentMaterials()).isEmpty();
        assertThat(result.activeTeachingMaps()).isEmpty();
    }

    @Test
    void getDashboardDoesNotRequireMaterialAnalysisForRecentMaterials() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        when(materialRepository.findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(material)));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        HomeDashboardResponse result = homeService.getDashboard(USER_ID);

        assertThat(result.recentMaterials().get(0).title()).isEqualTo("Material");
    }

    @Test
    void getDashboardPassesCurrentUserIdToRepositories() {
        when(materialRepository.findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        homeService.getDashboard(USER_ID);

        verify(materialRepository).findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class));
        verify(teachingMapRepository).findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        );
        verify(materialRepository, never()).findHomeRecentMaterials(eq(OTHER_USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class));
    }

    @Test
    void getDashboardAllowsNullPlatformType() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        ReflectionTestUtils.setField(material, "platformType", null);
        when(materialRepository.findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(material)));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        HomeDashboardResponse result = homeService.getDashboard(USER_ID);

        assertThat(result.recentMaterials().get(0).platformType()).isNull();
        assertThat(result.recentMaterials().get(0).platformImageUrl()).isNull();
    }

    @Test
    void getDashboardAllowsNullTeachingMapType() {
        TeachingMap teachingMap = teachingMap(201L, USER_ID, "Map", TeachingMapType.SHORTCUT, createdAt(1));
        ReflectionTestUtils.setField(teachingMap, "type", null);
        when(materialRepository.findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(teachingMap)));

        HomeDashboardResponse result = homeService.getDashboard(USER_ID);

        assertThat(result.activeTeachingMaps().get(0).type()).isNull();
    }

    @Test
    void getDashboardAppliesMaterialLimitSixAndTeachingMapLimitThree() {
        when(materialRepository.findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        homeService.getDashboard(USER_ID);

        ArgumentCaptor<Pageable> materialPageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Pageable> teachingMapPageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(materialRepository).findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), materialPageableCaptor.capture());
        verify(teachingMapRepository).findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                teachingMapPageableCaptor.capture()
        );
        assertThat(materialPageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(teachingMapPageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void getDashboardMapsMaterialAndTeachingMapFields() {
        Material material = material(101L, USER_ID, "Original", PlatformType.YOUTUBE, AiStatus.COMPLETED, createdAt(1));
        TeachingMap teachingMap = teachingMap(201L, USER_ID, "Teaching", TeachingMapType.DEEPDIVE, createdAt(2));
        when(materialRepository.findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(material)));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(teachingMap)));
        when(teachingMapStepRepository.findActivePlatformTypesByTeachingMapIdIn(List.of(201L), USER_ID))
                .thenReturn(List.of(
                        platformProjection(201L, PlatformType.YOUTUBE),
                        platformProjection(201L, PlatformType.VELOG),
                        platformProjection(201L, PlatformType.BLOG),
                        platformProjection(201L, PlatformType.NOTION),
                        platformProjection(201L, PlatformType.YOUTUBE)
                ));

        HomeDashboardResponse result = homeService.getDashboard(USER_ID);

        assertThat(result.recentMaterials().get(0).title()).isEqualTo("Original");
        assertThat(result.recentMaterials().get(0).platformImageUrl()).isEqualTo(PlatformType.YOUTUBE.getIconPath());
        assertThat(result.activeTeachingMaps().get(0).title()).isEqualTo("Teaching");
        assertThat(result.activeTeachingMaps().get(0).type()).isEqualTo("DEEPDIVE");
        assertThat(result.activeTeachingMaps().get(0).status()).isEqualTo("IN_PROGRESS");
        assertThat(result.activeTeachingMaps().get(0).sourcePlatforms()).hasSize(3);
        assertThat(result.activeTeachingMaps().get(0).sourcePlatforms())
                .extracting("imageUrl")
                .containsExactly(
                        PlatformType.YOUTUBE.getIconPath(),
                        PlatformType.VELOG.getIconPath(),
                        PlatformType.BLOG.getIconPath()
                );
    }

    @Test
    void getDashboardDoesNotThrowWhenMaterialAiStatusIsNull() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        ReflectionTestUtils.setField(material, "aiStatus", null);
        when(materialRepository.findHomeRecentMaterials(eq(USER_ID), eq(AiStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(material)));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        assertThatCode(() -> homeService.getDashboard(USER_ID))
                .doesNotThrowAnyException();
    }

    private Material material(
            Long materialId,
            Long userId,
            String title,
            PlatformType platformType,
            AiStatus aiStatus,
            LocalDateTime createdAt
    ) {
        User user = user(userId);
        Folder folder = Folder.create(user, "Folder");
        ReflectionTestUtils.setField(folder, "id", FOLDER_ID);
        Material material = Material.create(user, folder, title, "https://example.com", platformType);
        ReflectionTestUtils.setField(material, "id", materialId);
        ReflectionTestUtils.setField(material, "aiStatus", aiStatus);
        ReflectionTestUtils.setField(material, "createdAt", createdAt);
        return material;
    }

    private TeachingMap teachingMap(
            Long teachingMapId,
            Long userId,
            String title,
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
        ReflectionTestUtils.setField(teachingMap, "createdAt", createdAt);
        return teachingMap;
    }

    private TeachingMapPlatformProjection platformProjection(Long teachingMapId, PlatformType platformType) {
        return new TeachingMapPlatformProjection() {
            @Override
            public Long getTeachingMapId() {
                return teachingMapId;
            }

            @Override
            public PlatformType getPlatformType() {
                return platformType;
            }
        };
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
