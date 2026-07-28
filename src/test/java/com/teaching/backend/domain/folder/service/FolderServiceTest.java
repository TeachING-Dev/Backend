package com.teaching.backend.domain.folder.service;

import com.teaching.backend.domain.folder.dto.request.FolderCreateRequest;
import com.teaching.backend.domain.folder.dto.request.FolderRenameRequest;
import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        User user = User.create("user" + userId + "@example.com", "user" + userId, null, null, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Folder folder(Long userId, Long folderId, String name) {
        Folder folder = Folder.create(user(userId), name);
        ReflectionTestUtils.setField(folder, "id", folderId);
        return folder;
    }
}
