package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.dto.MaterialListResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.dto.request.MaterialFinalizeRequest;
import com.teaching.backend.domain.material.dto.response.MaterialFinalizeResponse;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.entity.Tag;
import com.teaching.backend.domain.tag.exception.TagErrorCode;
import com.teaching.backend.domain.tag.exception.TagException;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.tag.repository.TagRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialAnalysisRepository materialAnalysisRepository;

    @Mock
    private MaterialTagRepository materialTagRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private MaterialIndexingService materialIndexingService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private MaterialService materialService;

    @Test
    void getMaterialListReturnsCurrentUsersActiveMaterialsInRecentOrder() {
        Material newer = material(101L, USER_ID, "New", PlatformType.YOUTUBE, AiStatus.COMPLETED, createdAt(2));
        Material older = material(102L, USER_ID, "Old", PlatformType.BLOG, AiStatus.PENDING, createdAt(1));
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of(newer, older));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L, 102L)))
                .thenReturn(List.of(analysis(newer, "summary")));

        List<MaterialListResponse> result = materialService.getMaterialList(USER_ID, null);

        assertThat(result).extracting(MaterialListResponse::materialId)
                .containsExactly(101L, 102L);
        verify(materialRepository).findAllByUser_Id(eq(USER_ID), any(Sort.class));
    }

    @Test
    void getMaterialListUsesPageableWhenSizeExists() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.ANALYZING, createdAt(1));
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(material)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L)))
                .thenReturn(List.of());

        List<MaterialListResponse> result = materialService.getMaterialList(USER_ID, 1);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(materialRepository).findAllByUser_Id(eq(USER_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
        assertThat(result).hasSize(1);
    }

    @Test
    void getMaterialListReturnsEmptyListWhenNoMaterials() {
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of());

        List<MaterialListResponse> result = materialService.getMaterialList(USER_ID, null);

        assertThat(result).isEmpty();
        verify(materialAnalysisRepository, never()).findAllActiveByMaterialIds(any());
    }

    @Test
    void getMaterialListAllowsMissingMaterialAnalysis() {
        Material material = material(101L, USER_ID, "Material", PlatformType.PDF, AiStatus.PENDING, createdAt(1));
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of(material));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L)))
                .thenReturn(List.of());

        List<MaterialListResponse> result = materialService.getMaterialList(USER_ID, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).summary()).isNull();
    }

    @Test
    void getMaterialListQueriesOnlyCurrentUserMaterials() {
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of());

        materialService.getMaterialList(USER_ID, null);

        verify(materialRepository).findAllByUser_Id(eq(USER_ID), any(Sort.class));
        verify(materialRepository, never()).findAllByUser_Id(eq(OTHER_USER_ID), any(Sort.class));
    }

    @Test
    void getMaterialListMapsMaterialAndAnalysisFields() {
        LocalDateTime createdAt = createdAt(1);
        Material material = material(101L, USER_ID, "Original Title", PlatformType.YOUTUBE, AiStatus.COMPLETED, createdAt);
        material.completeAnalysis("Analysis Title", 2);
        MaterialAnalysis analysis = analysis(material, "Mapped summary");
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of(material));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L)))
                .thenReturn(List.of(analysis));

        MaterialListResponse result = materialService.getMaterialList(USER_ID, null).get(0);

        assertThat(result.materialId()).isEqualTo(101L);
        assertThat(result.title()).isEqualTo("Original Title");
        assertThat(result.analysisTitle()).isEqualTo("Analysis Title");
        assertThat(result.summary()).isEqualTo("Mapped summary");
        assertThat(result.platformType()).isEqualTo("YOUTUBE");
        assertThat(result.difficulty()).isEqualTo(2);
        assertThat(result.aiStatus()).isEqualTo("COMPLETED");
        assertThat(result.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void getMaterialListMapsPlatformIconPathToPlatformImageUrl() {
        Material material = material(101L, USER_ID, "Material", PlatformType.YOUTUBE, AiStatus.PENDING, createdAt(1));
        when(materialRepository.findAllByUser_Id(eq(USER_ID), any(Sort.class)))
                .thenReturn(List.of(material));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L)))
                .thenReturn(List.of());

        MaterialListResponse result = materialService.getMaterialList(USER_ID, null).get(0);

        assertThat(result.platformImageUrl()).isEqualTo(PlatformType.YOUTUBE.getIconPath());
    }

    @Test
    void getMaterialListRejectsNonPositiveSize() {
        assertThatThrownBy(() -> materialService.getMaterialList(USER_ID, 0))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.BAD_REQUEST);
    }

    @Test
    void deleteMaterialTagDeletesOnlyOwnedMaterialTag() {
        MaterialTag materialTag = materialTag(201L, USER_ID);
        when(materialTagRepository.findByIdWithMaterialAndUser(201L))
                .thenReturn(Optional.of(materialTag));

        assertThatCode(() -> materialService.deleteMaterialTag(USER_ID, 201L))
                .doesNotThrowAnyException();

        verify(materialTagRepository).delete(materialTag);
    }

    @Test
    void finalizeMaterialChangesFolderAndSynchronizesFinalTags() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        Folder finalFolder = folder(USER_ID, 20L);
        MaterialTag first = materialTagWithTagId(material, 1001L, "a");
        MaterialTag second = materialTagWithTagId(material, 1002L, "b");
        MaterialTag third = materialTagWithTagId(material, 1003L, "c");
        when(materialRepository.findByIdAndUser_Id(101L, USER_ID)).thenReturn(Optional.of(material));
        when(folderRepository.findByIdAndUser_Id(20L, USER_ID)).thenReturn(Optional.of(finalFolder));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(101L))).thenReturn(List.of(first, second, third));
        when(tagRepository.findAllById(List.of(1001L, 1003L))).thenReturn(List.of(first.getTag(), third.getTag()));

        MaterialFinalizeResponse response = materialService.finalizeMaterial(
                USER_ID,
                101L,
                new MaterialFinalizeRequest(20L, List.of(1001L, 1003L, 1003L))
        );

        assertThat(response.folderId()).isEqualTo(20L);
        assertThat(response.tagIds()).containsExactly(1001L, 1003L);
        assertThat(material.getFolderId()).isEqualTo(20L);
        verify(materialTagRepository).deleteAll(List.of(second));
        verify(materialTagRepository, never()).saveAll(any());
        verify(entityManager).flush();
        verify(materialIndexingService).syncFolderPayload(material);
    }

    @Test
    void finalizeMaterialCompensatesQdrantFolderPayloadWhenTransactionRollsBackAfterSync() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        Folder finalFolder = folder(USER_ID, 20L);
        MaterialTag first = materialTagWithTagId(material, 1001L, "a");
        when(materialRepository.findByIdAndUser_Id(101L, USER_ID)).thenReturn(Optional.of(material));
        when(materialRepository.findById(101L)).thenReturn(Optional.of(material));
        when(folderRepository.findByIdAndUser_Id(20L, USER_ID)).thenReturn(Optional.of(finalFolder));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(101L))).thenReturn(List.of(first));
        when(tagRepository.findAllById(List.of(1001L))).thenReturn(List.of(first.getTag()));

        TransactionSynchronizationManager.initSynchronization();
        try {
            materialService.finalizeMaterial(
                    USER_ID,
                    101L,
                    new MaterialFinalizeRequest(20L, List.of(1001L))
            );

            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(synchronization ->
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(materialIndexingService).syncFolderPayload(material);
        verify(materialIndexingService).syncFolderPayload(material, FOLDER_ID);
    }

    @Test
    void finalizeMaterialDoesNotCompensateQdrantFolderPayloadWhenTransactionCommits() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        Folder finalFolder = folder(USER_ID, 20L);
        MaterialTag first = materialTagWithTagId(material, 1001L, "a");
        when(materialRepository.findByIdAndUser_Id(101L, USER_ID)).thenReturn(Optional.of(material));
        when(folderRepository.findByIdAndUser_Id(20L, USER_ID)).thenReturn(Optional.of(finalFolder));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(101L))).thenReturn(List.of(first));
        when(tagRepository.findAllById(List.of(1001L))).thenReturn(List.of(first.getTag()));

        TransactionSynchronizationManager.initSynchronization();
        try {
            materialService.finalizeMaterial(
                    USER_ID,
                    101L,
                    new MaterialFinalizeRequest(20L, List.of(1001L))
            );

            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(synchronization ->
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(materialIndexingService).syncFolderPayload(material);
        verify(materialIndexingService, never()).syncFolderPayload(material, FOLDER_ID);
    }

    @Test
    void finalizeMaterialDoesNotRegisterQdrantCompensationWhenFolderDoesNotChange() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        Folder sameFolder = material.getFolder();
        MaterialTag first = materialTagWithTagId(material, 1001L, "a");
        when(materialRepository.findByIdAndUser_Id(101L, USER_ID)).thenReturn(Optional.of(material));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(sameFolder));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(101L))).thenReturn(List.of(first));
        when(tagRepository.findAllById(List.of(1001L))).thenReturn(List.of(first.getTag()));

        TransactionSynchronizationManager.initSynchronization();
        try {
            materialService.finalizeMaterial(
                    USER_ID,
                    101L,
                    new MaterialFinalizeRequest(FOLDER_ID, List.of(1001L))
            );

            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(materialIndexingService, never()).syncFolderPayload(any());
    }

    @Test
    void finalizeMaterialAllowsEmptyFinalTagsWithoutDeletingTagRows() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        Folder sameFolder = material.getFolder();
        MaterialTag first = materialTagWithTagId(material, 1001L, "a");
        when(materialRepository.findByIdAndUser_Id(101L, USER_ID)).thenReturn(Optional.of(material));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(sameFolder));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(101L))).thenReturn(List.of(first));

        MaterialFinalizeResponse response = materialService.finalizeMaterial(
                USER_ID,
                101L,
                new MaterialFinalizeRequest(FOLDER_ID, List.of())
        );

        assertThat(response.tagIds()).isEmpty();
        verify(materialTagRepository).deleteAll(List.of(first));
        verify(materialIndexingService, never()).syncFolderPayload(any());
    }

    @Test
    void finalizeMaterialReattachesExistingDetachedTag() {
        Material material = material(327L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        Folder sameFolder = material.getFolder();
        MaterialTag spec = materialTagWithTagId(material, 65L, "명세서");
        MaterialTag endpoint = materialTagWithTagId(material, 66L, "엔드포인트");
        MaterialTag social = materialTagWithTagId(material, 67L, "소셜 로그인");
        MaterialTag collaboration = materialTagWithTagId(material, 68L, "협업");
        Tag api = tag(56L, "API");
        when(materialRepository.findByIdAndUser_Id(327L, USER_ID)).thenReturn(Optional.of(material));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(sameFolder));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(327L)))
                .thenReturn(List.of(spec, endpoint, social, collaboration));
        when(tagRepository.findAllById(List.of(56L, 65L, 66L, 67L, 68L)))
                .thenReturn(List.of(api, spec.getTag(), endpoint.getTag(), social.getTag(), collaboration.getTag()));

        MaterialFinalizeResponse response = materialService.finalizeMaterial(
                USER_ID,
                327L,
                new MaterialFinalizeRequest(FOLDER_ID, List.of(56L, 65L, 66L, 67L, 68L))
        );

        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(materialTagRepository).saveAll(captor.capture());
        List<MaterialTag> savedTags = StreamSupport.stream(captor.getValue().spliterator(), false)
                .map(MaterialTag.class::cast)
                .toList();
        assertThat(savedTags).singleElement()
                .satisfies(materialTag -> {
                    assertThat(materialTag.getMaterial()).isSameAs(material);
                    assertThat(materialTag.getTag().getId()).isEqualTo(56L);
                    assertThat(materialTag.getTag().getName()).isEqualTo("API");
                });
        assertThat(response.tagIds()).containsExactly(56L, 65L, 66L, 67L, 68L);
        verify(materialTagRepository, never()).deleteAll(any());
        verify(materialIndexingService, never()).syncFolderPayload(any());
    }

    @Test
    void finalizeMaterialRemovesOnlyCurrentMaterialRelationAndKeepsSharedTagReusable() {
        Material material = material(327L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        Material otherMaterial = material(328L, USER_ID, "Other", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        Folder sameFolder = material.getFolder();
        Tag sharedApi = tag(56L, "API");
        MaterialTag currentRelation = MaterialTag.create(material, sharedApi);
        MaterialTag otherRelation = MaterialTag.create(otherMaterial, sharedApi);
        MaterialTag keptRelation = materialTagWithTagId(material, 65L, "명세서");
        when(materialRepository.findByIdAndUser_Id(327L, USER_ID)).thenReturn(Optional.of(material));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(sameFolder));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(327L)))
                .thenReturn(List.of(currentRelation, keptRelation));
        when(tagRepository.findAllById(List.of(65L))).thenReturn(List.of(keptRelation.getTag()));

        MaterialFinalizeResponse response = materialService.finalizeMaterial(
                USER_ID,
                327L,
                new MaterialFinalizeRequest(FOLDER_ID, List.of(65L))
        );

        verify(materialTagRepository).deleteAll(List.of(currentRelation));
        assertThat(otherRelation.getTag()).isSameAs(sharedApi);
        assertThat(response.tagIds()).containsExactly(65L);
        verify(materialTagRepository, never()).saveAll(any());
    }

    @Test
    void finalizeMaterialRejectsMissingTagBeforeMutation() {
        Material material = material(101L, USER_ID, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        Folder finalFolder = folder(USER_ID, 20L);
        MaterialTag first = materialTagWithTagId(material, 1001L, "a");
        when(materialRepository.findByIdAndUser_Id(101L, USER_ID)).thenReturn(Optional.of(material));
        when(folderRepository.findByIdAndUser_Id(20L, USER_ID)).thenReturn(Optional.of(finalFolder));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(101L))).thenReturn(List.of(first));
        when(tagRepository.findAllById(List.of(9999L))).thenReturn(List.of());

        assertTagExceptionThrown(
                () -> materialService.finalizeMaterial(
                        USER_ID,
                        101L,
                        new MaterialFinalizeRequest(20L, List.of(9999L))
                ),
                TagErrorCode.TAG_NOT_FOUND
        );

        assertThat(material.getFolderId()).isEqualTo(FOLDER_ID);
        verify(materialTagRepository, never()).deleteAll(any());
        verify(materialTagRepository, never()).saveAll(any());
        verify(entityManager, never()).flush();
        verify(materialIndexingService, never()).syncFolderPayload(any());
    }

    @Test
    void deleteMaterialTagFailsWhenMaterialTagDoesNotExist() {
        when(materialTagRepository.findByIdWithMaterialAndUser(201L))
                .thenReturn(Optional.empty());

        assertTagExceptionThrown(
                () -> materialService.deleteMaterialTag(USER_ID, 201L),
                TagErrorCode.TAG_NOT_FOUND
        );
        verify(materialTagRepository, never()).delete(any(MaterialTag.class));
    }

    @Test
    void deleteMaterialTagFailsWhenMaterialBelongsToOtherUser() {
        MaterialTag materialTag = materialTag(201L, OTHER_USER_ID);
        when(materialTagRepository.findByIdWithMaterialAndUser(201L))
                .thenReturn(Optional.of(materialTag));

        assertTagExceptionThrown(
                () -> materialService.deleteMaterialTag(USER_ID, 201L),
                TagErrorCode.TAG_ACCESS_DENIED
        );
        verify(materialTagRepository, never()).delete(any(MaterialTag.class));
    }

    @Test
    void deleteMaterialTagRejectsInvalidIdAsNotFound() {
        assertTagExceptionThrown(
                () -> materialService.deleteMaterialTag(USER_ID, 0L),
                TagErrorCode.TAG_NOT_FOUND
        );
        verify(materialTagRepository, never()).delete(any(MaterialTag.class));
    }

    private void assertTagExceptionThrown(Runnable action, TagErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(TagException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
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

    private Folder folder(Long userId, Long folderId) {
        Folder folder = Folder.create(user(userId), "Folder");
        ReflectionTestUtils.setField(folder, "id", folderId);
        return folder;
    }

    private MaterialAnalysis analysis(Material material, String summary) {
        MaterialAnalysis analysis = MaterialAnalysis.create(material, summary, "detail", "v1");
        ReflectionTestUtils.setField(analysis, "id", material.getId() + 1000);
        return analysis;
    }

    private MaterialTag materialTag(Long materialTagId, Long userId) {
        Material material = material(101L, userId, "Material", PlatformType.WEB, AiStatus.COMPLETED, createdAt(1));
        Tag tag = Tag.create("tag");
        MaterialTag materialTag = MaterialTag.create(material, tag);
        ReflectionTestUtils.setField(materialTag, "id", materialTagId);
        return materialTag;
    }

    private MaterialTag materialTagWithTagId(Material material, Long tagId, String tagName) {
        return MaterialTag.create(material, tag(tagId, tagName));
    }

    private Tag tag(Long tagId, String tagName) {
        Tag tag = Tag.create(tagName);
        ReflectionTestUtils.setField(tag, "id", tagId);
        return tag;
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
