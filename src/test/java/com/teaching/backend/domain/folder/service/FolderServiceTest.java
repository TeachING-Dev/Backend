package com.teaching.backend.domain.folder.service;

import com.teaching.backend.domain.folder.dto.request.FolderCreateRequest;
import com.teaching.backend.domain.folder.dto.request.FolderRenameRequest;
import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.enums.MembershipType;
import com.teaching.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FOLDER_ID = 10L;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialAnalysisRepository materialAnalysisRepository;

    @Mock
    private MaterialTagRepository materialTagRepository;

    @InjectMocks
    private FolderService folderService;

    @Test
    void createFolderAllowsKoreanEnglishAndMixedNames() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.existsActiveByUserIdAndName(eq(USER_ID), any())).thenReturn(false);
        when(folderRepository.saveAndFlush(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> folderService.createFolder(USER_ID, new FolderCreateRequest("백엔드")))
                .doesNotThrowAnyException();
        assertThatCode(() -> folderService.createFolder(USER_ID, new FolderCreateRequest("Backend")))
                .doesNotThrowAnyException();
        assertThatCode(() -> folderService.createFolder(USER_ID, new FolderCreateRequest("백엔드Backend")))
                .doesNotThrowAnyException();
        assertThatCode(() -> folderService.createFolder(USER_ID, new FolderCreateRequest("가나다라마바사아자차")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "백엔드1", "백엔드!", "백 엔드", "$^%^$%", "ㄱ", "ㅏ", " backend", "backend "})
    void createFolderRejectsInvalidNameFormat(String folderName) {
        assertFolderExceptionThrown(
                () -> folderService.createFolder(USER_ID, new FolderCreateRequest(folderName)),
                FolderErrorCode.INVALID_FOLDER_NAME_FORMAT
        );
        verify(folderRepository, never()).saveAndFlush(any(Folder.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "백엔드1", "백엔드!", "백 엔드", "$^%^$%", "ㄱ", "ㅏ", " backend", "backend "})
    void renameFolderRejectsInvalidNameFormat(String folderName) {
        assertFolderExceptionThrown(
                () -> folderService.renameFolder(USER_ID, FOLDER_ID, new FolderRenameRequest(folderName)),
                FolderErrorCode.INVALID_FOLDER_NAME_FORMAT
        );
        verify(folderRepository, never()).findByIdAndUser_Id(any(), any());
    }

    @Test
    void createFolderRejectsNameLongerThanTenCharacters() {
        assertFolderExceptionThrown(
                () -> folderService.createFolder(USER_ID, new FolderCreateRequest("열한글자폴더이름테스트")),
                FolderErrorCode.FOLDER_NAME_TOO_LONG
        );
        verify(folderRepository, never()).saveAndFlush(any(Folder.class));
    }

    @Test
    void createFolderAllowsSixthActiveFolderForFreeUser() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.countByUser_Id(USER_ID)).thenReturn(5L);
        when(folderRepository.existsActiveByUserIdAndName(USER_ID, "Backend")).thenReturn(false);
        when(folderRepository.saveAndFlush(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> folderService.createFolder(USER_ID, new FolderCreateRequest("Backend")))
                .doesNotThrowAnyException();
    }

    @Test
    void createFolderFailsWhenActiveFolderLimitExceeded() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.countByUser_Id(USER_ID)).thenReturn(6L);

        assertFolderExceptionThrown(
                () -> folderService.createFolder(USER_ID, new FolderCreateRequest("Backend")),
                FolderErrorCode.FOLDER_LIMIT_EXCEEDED
        );
        verify(folderRepository, never()).saveAndFlush(any(Folder.class));
    }

    @Test
    void createFolderDoesNotApplyFreeLimitToPremiumUser() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID, MembershipType.PREMIUM)));
        when(folderRepository.existsActiveByUserIdAndName(USER_ID, "Backend")).thenReturn(false);
        when(folderRepository.saveAndFlush(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> folderService.createFolder(USER_ID, new FolderCreateRequest("Backend")))
                .doesNotThrowAnyException();
        verify(folderRepository, never()).countByUser_Id(USER_ID);
    }

    @Test
    void createFolderFailsWhenSameUserHasActiveFolderWithSameName() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.existsActiveByUserIdAndName(USER_ID, "Backend")).thenReturn(true);

        assertDuplicateNameThrown(() -> folderService.createFolder(USER_ID, new FolderCreateRequest("Backend")));
        verify(folderRepository, never()).saveAndFlush(any(Folder.class));
    }

    @Test
    void createFolderAllowsNameUsedOnlyByDeletedFolder() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.existsActiveByUserIdAndName(USER_ID, "Backend")).thenReturn(false);
        when(folderRepository.saveAndFlush(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> folderService.createFolder(USER_ID, new FolderCreateRequest("Backend")))
                .doesNotThrowAnyException();
    }

    @Test
    void restoreFolderFailsWhenActiveFolderWithSameNameExists() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.empty());
        when(folderRepository.countByIdIncludingDeleted(FOLDER_ID)).thenReturn(1L);
        when(folderRepository.countByIdAndUserIdIncludingDeleted(FOLDER_ID, USER_ID)).thenReturn(1L);
        when(folderRepository.countDeletedByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(1L);
        when(folderRepository.countActiveNameConflictForRestore(FOLDER_ID, USER_ID)).thenReturn(1L);

        assertDuplicateNameThrown(() -> folderService.restoreFolder(USER_ID, FOLDER_ID));
        verify(folderRepository, never()).restoreDeletedFolder(FOLDER_ID, USER_ID);
    }

    @Test
    void restoreDeletedFolderFailsWhenDeletedNameWasReusedByNewActiveFolder() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.empty());
        when(folderRepository.countByIdIncludingDeleted(FOLDER_ID)).thenReturn(1L);
        when(folderRepository.countByIdAndUserIdIncludingDeleted(FOLDER_ID, USER_ID)).thenReturn(1L);
        when(folderRepository.countDeletedByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(1L);
        when(folderRepository.countActiveNameConflictForRestore(FOLDER_ID, USER_ID)).thenReturn(1L);

        assertDuplicateNameThrown(() -> folderService.restoreFolder(USER_ID, FOLDER_ID));
        verify(folderRepository, never()).restoreDeletedFolder(FOLDER_ID, USER_ID);
    }

    @Test
    void restoreFolderAlsoRestoresMaterialsTrashedTogetherWithFolder() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.empty());
        when(folderRepository.countByIdIncludingDeleted(FOLDER_ID)).thenReturn(1L);
        when(folderRepository.countByIdAndUserIdIncludingDeleted(FOLDER_ID, USER_ID)).thenReturn(1L);
        when(folderRepository.countDeletedByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(1L);
        when(folderRepository.countActiveNameConflictForRestore(FOLDER_ID, USER_ID)).thenReturn(0L);
        when(folderRepository.restoreDeletedFolder(FOLDER_ID, USER_ID)).thenReturn(1);

        folderService.restoreFolder(USER_ID, FOLDER_ID);

        verify(materialRepository).restoreTrashedMaterialsByFolder(FOLDER_ID, USER_ID);
    }

    @Test
    void restoreFolderDoesNotRestoreMaterialsWhenFolderRestoreFails() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.empty());
        when(folderRepository.countByIdIncludingDeleted(FOLDER_ID)).thenReturn(1L);
        when(folderRepository.countByIdAndUserIdIncludingDeleted(FOLDER_ID, USER_ID)).thenReturn(1L);
        when(folderRepository.countDeletedByIdAndUserId(FOLDER_ID, USER_ID)).thenReturn(1L);
        when(folderRepository.countActiveNameConflictForRestore(FOLDER_ID, USER_ID)).thenReturn(0L);
        when(folderRepository.restoreDeletedFolder(FOLDER_ID, USER_ID)).thenReturn(0);

        assertFolderExceptionThrown(
                () -> folderService.restoreFolder(USER_ID, FOLDER_ID),
                FolderErrorCode.FOLDER_NOT_FOUND
        );
        verify(materialRepository, never()).restoreTrashedMaterialsByFolder(any(), any());
    }

    @Test
    void renameFolderAllowsNameUsedOnlyByDeletedFolder() {
        Folder folder = folder(USER_ID, FOLDER_ID, "Java");
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder));
        when(folderRepository.existsActiveByUserIdAndNameAndIdNot(USER_ID, "Backend", FOLDER_ID)).thenReturn(false);

        assertThatCode(() -> folderService.renameFolder(USER_ID, FOLDER_ID, new FolderRenameRequest("Backend")))
                .doesNotThrowAnyException();

        InOrder inOrder = inOrder(userRepository, folderRepository);
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        inOrder.verify(folderRepository).findByIdAndUser_Id(FOLDER_ID, USER_ID);
        inOrder.verify(folderRepository).existsActiveByUserIdAndNameAndIdNot(USER_ID, "Backend", FOLDER_ID);
    }

    @Test
    void renameFolderFailsWhenAnotherActiveFolderHasSameName() {
        Folder folder = folder(USER_ID, FOLDER_ID, "Java");
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder));
        when(folderRepository.existsActiveByUserIdAndNameAndIdNot(USER_ID, "Backend", FOLDER_ID)).thenReturn(true);

        assertDuplicateNameThrown(() -> folderService.renameFolder(USER_ID, FOLDER_ID, new FolderRenameRequest("Backend")));
        verify(folderRepository, never()).flush();
    }

    @Test
    void renameFolderAllowsSameNameForCurrentFolder() {
        Folder folder = folder(USER_ID, FOLDER_ID, "Backend");
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder));

        assertThatCode(() -> folderService.renameFolder(USER_ID, FOLDER_ID, new FolderRenameRequest("Backend")))
                .doesNotThrowAnyException();
        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(folderRepository, never()).existsActiveByUserIdAndNameAndIdNot(USER_ID, "Backend", FOLDER_ID);
        verify(folderRepository, never()).flush();
    }

    @Test
    void createFolderAllowsSameNameForDifferentUser() {
        when(userRepository.findByIdForUpdate(OTHER_USER_ID)).thenReturn(Optional.of(user(OTHER_USER_ID)));
        when(folderRepository.existsActiveByUserIdAndName(OTHER_USER_ID, "Backend")).thenReturn(false);
        when(folderRepository.saveAndFlush(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> folderService.createFolder(OTHER_USER_ID, new FolderCreateRequest("Backend")))
                .doesNotThrowAnyException();
    }

    @Test
    void getOwnedFolderReturnsCurrentUsersFolder() {
        Folder folder = folder(USER_ID, FOLDER_ID, "Backend");
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder));

        assertThatCode(() -> folderService.getOwnedFolder(USER_ID, FOLDER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void getOwnedFolderFailsWhenFolderDoesNotExist() {
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.empty());
        when(folderRepository.existsById(FOLDER_ID)).thenReturn(false);

        assertFolderExceptionThrown(
                () -> folderService.getOwnedFolder(USER_ID, FOLDER_ID),
                FolderErrorCode.FOLDER_NOT_FOUND
        );
    }

    @Test
    void getOwnedFolderFailsWhenFolderBelongsToOtherUser() {
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.empty());
        when(folderRepository.existsById(FOLDER_ID)).thenReturn(true);

        assertFolderExceptionThrown(
                () -> folderService.getOwnedFolder(USER_ID, FOLDER_ID),
                FolderErrorCode.FOLDER_ACCESS_DENIED
        );
    }

    @Test
    void getOwnedFolderRejectsInvalidFolderId() {
        assertFolderExceptionThrown(
                () -> folderService.getOwnedFolder(USER_ID, 0L),
                FolderErrorCode.INVALID_FOLDER_ID
        );
    }

    @Test
    void getFolderMaterialsIncludesStoredPlatformTypeForEachMaterial() {
        Folder folder = folder(USER_ID, FOLDER_ID, "Backend");
        Material material = material(101L, USER_ID, folder, PlatformType.YOUTUBE);
        MaterialAnalysis analysis = MaterialAnalysis.create(material, "Summary", "Detail", "v1");
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder));
        when(materialRepository.searchFolderMaterials(
                eq(FOLDER_ID),
                eq(USER_ID),
                eq(null),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(material)));
        when(materialAnalysisRepository.findAllActiveByMaterialIds(List.of(101L))).thenReturn(List.of(analysis));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(101L))).thenReturn(List.of());

        var result = folderService.getFolderMaterials(USER_ID, FOLDER_ID, null, "recent", 0, 10);

        assertThat(result.content()).singleElement()
                .satisfies(item -> {
                    assertThat(item.materialId()).isEqualTo(101L);
                    assertThat(item.platformType()).isEqualTo("YOUTUBE");
                    assertThat(item.summary()).isEqualTo("Summary");
                });
    }

    private void assertDuplicateNameThrown(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(FolderException.class)
                .extracting("errorCode")
                .isEqualTo(FolderErrorCode.DUPLICATE_FOLDER_NAME);
    }

    private void assertFolderExceptionThrown(Runnable action, FolderErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(FolderException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private User user(Long userId) {
        return user(userId, MembershipType.FREE);
    }

    private User user(Long userId, MembershipType membershipType) {
        User user = User.create("user" + userId + "@example.com", "user" + userId, null, null, null);
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "membershipType", membershipType);
        return user;
    }

    private Folder folder(Long userId, Long folderId, String name) {
        Folder folder = Folder.create(user(userId), name);
        ReflectionTestUtils.setField(folder, "id", folderId);
        return folder;
    }

    private Material material(
            Long materialId,
            Long userId,
            Folder folder,
            PlatformType platformType
    ) {
        Material material = Material.create(
                user(userId),
                folder,
                "Material",
                "https://example.com",
                platformType
        );
        ReflectionTestUtils.setField(material, "id", materialId);
        ReflectionTestUtils.setField(material, "aiStatus", AiStatus.COMPLETED);
        ReflectionTestUtils.setField(material, "createdAt", LocalDateTime.of(2026, 7, 31, 10, 0));
        ReflectionTestUtils.setField(material, "updatedAt", LocalDateTime.of(2026, 7, 31, 11, 0));
        return material;
    }
}
