package com.teaching.backend.domain.home.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.home.dto.HomeDashboardResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialAnalysisRepository materialAnalysisRepository;

    @Mock
    private TeachingMapRepository teachingMapRepository;

    @InjectMocks
    private HomeService homeService;

    @Test
    void getDashboardMapsSixRecentMaterialsAndThreeActiveTeachingMaps() {
        List<Material> materials = List.of(
                material(101L, USER_ID, "Material 1", PlatformType.YOUTUBE, AiStatus.COMPLETED, createdAt(1)),
                material(102L, USER_ID, "Material 2", PlatformType.BLOG, AiStatus.PENDING, createdAt(2)),
                material(103L, USER_ID, "Material 3", PlatformType.PDF, AiStatus.FAILED, createdAt(3)),
                material(104L, USER_ID, "Material 4", PlatformType.WEB, AiStatus.ANALYZING, createdAt(4)),
                material(105L, USER_ID, "Material 5", PlatformType.NOTION, AiStatus.CRAWLING, createdAt(5)),
                material(106L, USER_ID, "Material 6", PlatformType.YOUTUBE, AiStatus.MANUAL_SAVED, createdAt(6))
        );
        List<TeachingMap> teachingMaps = List.of(
                teachingMap(201L, USER_ID, "Map 1", TeachingMapType.SHORTCUT, createdAt(1)),
                teachingMap(202L, USER_ID, "Map 2", TeachingMapType.DEEPDIVE, createdAt(2)),
                teachingMap(203L, USER_ID, "Map 3", TeachingMapType.SHORTCUT, createdAt(3))
        );
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(materials));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L, 102L, 103L, 104L, 105L, 106L)))
                .thenReturn(List.of(analysis(materials.get(0), "Summary 1")));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(teachingMaps));

        HomeDashboardResponse result = homeService.getDashboard(USER_ID);

        assertThat(result.recentMaterials()).hasSize(6);
        assertThat(result.activeTeachingMaps()).hasSize(3);
        assertThat(result.recentMaterials().get(0).materialId()).isEqualTo(101L);
        assertThat(result.recentMaterials().get(0).summary()).isEqualTo("Summary 1");
        assertThat(result.activeTeachingMaps().get(0).teachingMapId()).isEqualTo(201L);
    }

    @Test
    void getDashboardReturnsEmptyListsWhenNoDataExists() {
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
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
    void getDashboardAllowsMaterialWithoutAnalysis() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.PENDING, createdAt(1));
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(material)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L)))
                .thenReturn(List.of());
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        HomeDashboardResponse result = homeService.getDashboard(USER_ID);

        assertThat(result.recentMaterials().get(0).summary()).isNull();
    }

    @Test
    void getDashboardQueriesMaterialAnalysisInBatch() {
        Material first = material(101L, USER_ID, "First", PlatformType.YOUTUBE, AiStatus.COMPLETED, createdAt(1));
        Material second = material(102L, USER_ID, "Second", PlatformType.BLOG, AiStatus.COMPLETED, createdAt(2));
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L, 102L)))
                .thenReturn(List.of(analysis(first, "First summary"), analysis(second, "Second summary")));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        HomeDashboardResponse result = homeService.getDashboard(USER_ID);

        verify(materialAnalysisRepository).findAllActiveByMaterialIds(List.of(101L, 102L));
        verify(materialAnalysisRepository, never()).findByMaterialId(any());
        assertThat(result.recentMaterials()).extracting("summary")
                .containsExactly("First summary", "Second summary");
    }

    @Test
    void getDashboardPassesCurrentUserIdToRepositories() {
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        homeService.getDashboard(USER_ID);

        verify(materialRepository).findAllByUser_Id(eq(USER_ID), any(Pageable.class));
        verify(teachingMapRepository).findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        );
        verify(materialRepository, never()).findAllByUser_Id(eq(OTHER_USER_ID), any(Pageable.class));
    }

    @Test
    void getDashboardAllowsNullPlatformType() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.PENDING, createdAt(1));
        ReflectionTestUtils.setField(material, "platformType", null);
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(material)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L)))
                .thenReturn(List.of());
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
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
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
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        homeService.getDashboard(USER_ID);

        ArgumentCaptor<Pageable> materialPageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Pageable> teachingMapPageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(materialRepository).findAllByUser_Id(eq(USER_ID), materialPageableCaptor.capture());
        verify(teachingMapRepository).findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                teachingMapPageableCaptor.capture()
        );
        assertThat(materialPageableCaptor.getValue().getPageSize()).isEqualTo(6);
        assertThat(teachingMapPageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void getDashboardMapsMaterialAndTeachingMapFields() {
        Material material = material(101L, USER_ID, "Original", PlatformType.YOUTUBE, AiStatus.COMPLETED, createdAt(1));
        material.completeAnalysis("Analyzed", 2);
        TeachingMap teachingMap = teachingMap(201L, USER_ID, "Teaching", TeachingMapType.DEEPDIVE, createdAt(2));
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(material)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L)))
                .thenReturn(List.of(analysis(material, "Summary")));
        when(teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                eq(USER_ID),
                eq(TeachingMapStatus.IN_PROGRESS),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(teachingMap)));

        HomeDashboardResponse result = homeService.getDashboard(USER_ID);

        assertThat(result.recentMaterials().get(0).title()).isEqualTo("Original");
        assertThat(result.recentMaterials().get(0).analysisTitle()).isEqualTo("Analyzed");
        assertThat(result.recentMaterials().get(0).difficulty()).isEqualTo(2);
        assertThat(result.recentMaterials().get(0).platformImageUrl()).isEqualTo(PlatformType.YOUTUBE.getIconPath());
        assertThat(result.activeTeachingMaps().get(0).title()).isEqualTo("Teaching");
        assertThat(result.activeTeachingMaps().get(0).type()).isEqualTo("DEEPDIVE");
        assertThat(result.activeTeachingMaps().get(0).status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void getDashboardDoesNotThrowWhenMaterialAiStatusIsNull() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.PENDING, createdAt(1));
        ReflectionTestUtils.setField(material, "aiStatus", null);
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(material)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L)))
                .thenReturn(List.of());
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
        Material material = Material.create(user, folder, title, "https://example.com", platformType);
        ReflectionTestUtils.setField(material, "id", materialId);
        ReflectionTestUtils.setField(material, "aiStatus", aiStatus);
        ReflectionTestUtils.setField(material, "createdAt", createdAt);
        return material;
    }

    private MaterialAnalysis analysis(Material material, String summary) {
        MaterialAnalysis analysis = MaterialAnalysis.create(material, summary, "detail", "v1");
        ReflectionTestUtils.setField(analysis, "id", material.getId() + 1000);
        return analysis;
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

    private User user(Long userId) {
        User user = User.create("user" + userId + "@example.com", "user" + userId, null, null, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private LocalDateTime createdAt(int day) {
        return LocalDateTime.of(2026, 7, day, 10, 0);
    }
}
