package com.teaching.backend.domain.trash.service;

import com.teaching.backend.domain.folder.dto.request.FolderIdsRequest;
import com.teaching.backend.domain.folder.dto.response.FolderTrashRestoreResponse;
import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.entity.Tag;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapStepRepository;
import com.teaching.backend.domain.trash.dto.response.TrashFolderMaterialListResponse;
import com.teaching.backend.domain.trash.dto.response.TrashMaterialItemResponse;
import com.teaching.backend.domain.trash.dto.response.TrashMaterialListResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrashServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 100L;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialAnalysisRepository materialAnalysisRepository;

    @Mock
    private MaterialTagRepository materialTagRepository;

    @Mock
    private TeachingMapRepository teachingMapRepository;

    @Mock
    private TeachingMapStepRepository teachingMapStepRepository;

    @InjectMocks
    private TrashService trashService;

    @Test
    void groupsTagsUnderCorrectMaterialId() {
        Material materialA = material(10L);
        Material materialB = material(20L);
        when(materialRepository.findTrashedByUserIdOrderByDeletedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(materialA, materialB)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(anyList())).thenReturn(List.of());
        when(materialTagRepository.findAllWithTagByMaterialIds(anyList())).thenReturn(List.of(
                materialTag(materialA, tag(1L, "Spring")),
                materialTag(materialA, tag(3L, "JPA")),
                materialTag(materialB, tag(2L, "백엔드"))
        ));

        TrashMaterialListResponse response = trashService.getTrashedMaterials(USER_ID, null, 0);

        TrashMaterialItemResponse itemA = findById(response, 10L);
        TrashMaterialItemResponse itemB = findById(response, 20L);
        assertThat(itemA.tags()).hasSize(2);
        assertThat(itemA.tags()).extracting("tagId").containsExactlyInAnyOrder(1L, 3L);
        assertThat(itemA.tags()).extracting("tagName").containsExactlyInAnyOrder("Spring", "JPA");
        assertThat(itemB.tags()).extracting("tagId").containsExactly(2L);
        assertThat(itemB.tags()).extracting("tagName").containsExactly("백엔드");
    }

    @Test
    void returnsEmptyNotNullTagsForMaterialWithoutTags() {
        Material materialWithoutTags = material(30L);
        when(materialRepository.findTrashedByUserIdOrderByDeletedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(materialWithoutTags)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(anyList())).thenReturn(List.of());
        when(materialTagRepository.findAllWithTagByMaterialIds(anyList())).thenReturn(List.of());

        TrashMaterialListResponse response = trashService.getTrashedMaterials(USER_ID, null, 0);

        assertThat(response.content().get(0).tags()).isNotNull().isEmpty();
    }

    @Test
    void doesNotQueryTagRepositoryForEmptyPage() {
        when(materialRepository.findTrashedByUserIdOrderByDeletedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        trashService.getTrashedMaterials(USER_ID, null, 0);

        verify(materialTagRepository, never()).findAllWithTagByMaterialIds(anyList());
    }

    @Test
    void queriesTagRepositoryExactlyOnceForNonEmptyPage() {
        Material materialA = material(10L);
        Material materialB = material(20L);
        when(materialRepository.findTrashedByUserIdOrderByDeletedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(materialA, materialB)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(anyList())).thenReturn(List.of());
        when(materialTagRepository.findAllWithTagByMaterialIds(anyList())).thenReturn(List.of());

        trashService.getTrashedMaterials(USER_ID, null, 0);

        ArgumentCaptor<List<Long>> materialIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(materialTagRepository, times(1)).findAllWithTagByMaterialIds(materialIdsCaptor.capture());
        assertThat(materialIdsCaptor.getValue()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void restoreFoldersCascadesMaterialRestoreOnlyForActuallyRestoredFolders() {
        when(folderRepository.restoreTrashedFolderIfNameAvailable(10L, USER_ID)).thenReturn(1);
        when(folderRepository.restoreTrashedFolderIfNameAvailable(20L, USER_ID)).thenReturn(0);

        FolderTrashRestoreResponse response = trashService.restoreFolders(USER_ID, new FolderIdsRequest(List.of(10L, 20L)));

        assertThat(response.restoredIds()).containsExactly(10L);
        assertThat(response.failedIds()).containsExactly(20L);
        verify(materialRepository).restoreTrashedMaterialsByFolder(10L, USER_ID);
        verify(materialRepository, never()).restoreTrashedMaterialsByFolder(20L, USER_ID);
    }

    @Test
    void getTrashedFolderMaterialsReturnsFolderContents() {
        Folder folder = folder(FOLDER_ID, "Backend");
        Material materialA = material(10L);
        when(folderRepository.countByIdIncludingDeleted(FOLDER_ID)).thenReturn(1L);
        when(folderRepository.countByIdAndUserIdIncludingDeleted(FOLDER_ID, USER_ID)).thenReturn(1L);
        when(folderRepository.findTrashedByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder));
        when(materialRepository.findTrashedByFolderIdOrderByDeletedAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(materialA)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(anyList())).thenReturn(List.of());
        when(materialTagRepository.findAllWithTagByMaterialIds(anyList())).thenReturn(List.of());

        TrashFolderMaterialListResponse response = trashService.getTrashedFolderMaterials(USER_ID, FOLDER_ID, null, 0);

        assertThat(response.folderId()).isEqualTo(FOLDER_ID);
        assertThat(response.folderName()).isEqualTo("Backend");
        assertThat(response.content()).extracting("materialId").containsExactly(10L);
    }

    @Test
    void getTrashedFolderMaterialsThrowsNotFoundWhenFolderDoesNotExist() {
        when(folderRepository.countByIdIncludingDeleted(FOLDER_ID)).thenReturn(0L);

        assertThatThrownBy(() -> trashService.getTrashedFolderMaterials(USER_ID, FOLDER_ID, null, 0))
                .isInstanceOf(FolderException.class)
                .extracting("errorCode")
                .isEqualTo(FolderErrorCode.FOLDER_NOT_FOUND);
    }

    @Test
    void getTrashedFolderMaterialsThrowsAccessDeniedWhenFolderBelongsToOtherUser() {
        when(folderRepository.countByIdIncludingDeleted(FOLDER_ID)).thenReturn(1L);
        when(folderRepository.countByIdAndUserIdIncludingDeleted(FOLDER_ID, USER_ID)).thenReturn(0L);

        assertThatThrownBy(() -> trashService.getTrashedFolderMaterials(USER_ID, FOLDER_ID, null, 0))
                .isInstanceOf(FolderException.class)
                .extracting("errorCode")
                .isEqualTo(FolderErrorCode.FOLDER_ACCESS_DENIED);
    }

    private TrashMaterialItemResponse findById(TrashMaterialListResponse response, Long materialId) {
        return response.content().stream()
                .filter(item -> item.materialId().equals(materialId))
                .findFirst()
                .orElseThrow();
    }

    private Material material(Long id) {
        Material material = Material.create(null, null, "title-" + id, "https://example.com/" + id, PlatformType.WEB);
        ReflectionTestUtils.setField(material, "id", id);
        return material;
    }

    private Folder folder(Long id, String name) {
        Folder folder = Folder.create(null, name);
        ReflectionTestUtils.setField(folder, "id", id);
        return folder;
    }

    private Tag tag(Long id, String name) {
        Tag tag = Tag.create(name);
        ReflectionTestUtils.setField(tag, "id", id);
        return tag;
    }

    private MaterialTag materialTag(Material material, Tag tag) {
        return MaterialTag.create(material, tag);
    }
}
