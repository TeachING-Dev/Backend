package com.teaching.backend.domain.folder.service;

import com.teaching.backend.domain.folder.dto.request.FolderCreateRequest;
import com.teaching.backend.domain.folder.dto.request.FolderRenameRequest;
import com.teaching.backend.domain.folder.dto.response.FolderCreateResponse;
import com.teaching.backend.domain.folder.dto.response.FolderDetailResponse;
import com.teaching.backend.domain.folder.dto.response.FolderListResponse;
import com.teaching.backend.domain.folder.dto.response.FolderRestoreResponse;
import com.teaching.backend.domain.folder.dto.response.FolderRenameResponse;
import com.teaching.backend.domain.folder.dto.response.FolderTrashResponse;
import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FolderService {

    private static final String DEFAULT_SORT = "recent";
    private static final int MAX_FOLDER_COUNT = 6;
    private static final int MAX_FOLDER_NAME_LENGTH = 10;

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    public List<FolderListResponse> getFolderList(
            Long userId,
            String sort
    ) {
        Sort folderSort = resolveSort(sort);

        List<Folder> folders = folderRepository.findAllByUser_Id(
                userId,
                folderSort
        );

        return folders.stream()
                .map(FolderListResponse::from)
                .toList();
    }

    public FolderDetailResponse getFolderDetail(
            Long userId,
            Long folderId
    ) {
        Folder folder = getOwnedFolder(userId, folderId);

        return FolderDetailResponse.from(folder);
    }

    @Transactional
    public FolderCreateResponse createFolder(
            Long userId,
            FolderCreateRequest request
    ) {
        String folderName = validateAndNormalizeFolderName(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        if (folderRepository.countByUser_Id(userId) >= MAX_FOLDER_COUNT) {
            throw new FolderException(FolderErrorCode.FOLDER_LIMIT_EXCEEDED);
        }

        if (folderRepository.existsByUser_IdAndName(userId, folderName)) {
            throw new FolderException(FolderErrorCode.DUPLICATE_FOLDER_NAME);
        }

        Folder folder = Folder.create(user, folderName);
        Folder savedFolder = folderRepository.save(folder);

        return FolderCreateResponse.from(savedFolder);
    }

    @Transactional
    public FolderRenameResponse renameFolder(
            Long userId,
            Long folderId,
            FolderRenameRequest request
    ) {
        String folderName = validateAndNormalizeFolderName(request);
        Folder folder = getOwnedFolder(userId, folderId);

        if (folder.getName().equals(folderName)) {
            return FolderRenameResponse.from(folder);
        }

        if (folderRepository.existsByUser_IdAndNameAndIdNot(userId, folderName, folderId)) {
            throw new FolderException(FolderErrorCode.DUPLICATE_FOLDER_NAME);
        }

        folder.rename(folderName);

        return FolderRenameResponse.from(folder);
    }

    @Transactional
    public FolderTrashResponse moveFolderToTrash(
            Long userId,
            Long folderId
    ) {
        validateFolderId(folderId);

        Folder folder = folderRepository.findByIdAndUser_Id(folderId, userId)
                .orElseThrow(() -> resolveTrashLookupException(userId, folderId));

        folder.delete();

        return FolderTrashResponse.from(folder);
    }

    @Transactional
    public FolderRestoreResponse restoreFolder(
            Long userId,
            Long folderId
    ) {
        validateFolderId(folderId);

        if (folderRepository.findByIdAndUser_Id(folderId, userId).isPresent()) {
            return FolderRestoreResponse.of(folderId, false);
        }

        validateRestorableFolder(userId, folderId);

        if (folderRepository.countByUser_Id(userId) >= MAX_FOLDER_COUNT) {
            throw new FolderException(FolderErrorCode.FOLDER_LIMIT_EXCEEDED);
        }

        if (folderRepository.countActiveNameConflictForRestore(folderId, userId) > 0) {
            throw new FolderException(FolderErrorCode.DUPLICATE_FOLDER_NAME);
        }

        int restoredCount = folderRepository.restoreDeletedFolder(folderId, userId);
        if (restoredCount == 0) {
            throw new FolderException(FolderErrorCode.FOLDER_NOT_FOUND);
        }

        return FolderRestoreResponse.of(folderId, false);
    }

    private Sort resolveSort(String sort) {
        String normalizedSort = normalizeSort(sort);

        return switch (normalizedSort) {
            case "recent" ->
                    Sort.by(
                            Sort.Direction.DESC,
                            "updatedAt"
                    );

            case "oldest" ->
                    Sort.by(
                            Sort.Direction.ASC,
                            "createdAt"
                    );

            case "name" ->
                    Sort.by(
                            Sort.Direction.ASC,
                            "name"
                    );

            default ->
                    throw new FolderException(
                            FolderErrorCode.INVALID_SORT
                    );
        };
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }

        return sort.trim().toLowerCase();
    }

    private String validateAndNormalizeFolderName(FolderCreateRequest request) {
        if (request == null || request.folderName() == null || request.folderName().isBlank()) {
            throw new FolderException(FolderErrorCode.FOLDER_NAME_REQUIRED);
        }

        String folderName = request.normalizedFolderName();
        if (folderName.length() > MAX_FOLDER_NAME_LENGTH) {
            throw new FolderException(FolderErrorCode.FOLDER_NAME_TOO_LONG);
        }

        return folderName;
    }

    private String validateAndNormalizeFolderName(FolderRenameRequest request) {
        if (request == null || request.folderName() == null || request.folderName().isBlank()) {
            throw new FolderException(FolderErrorCode.FOLDER_NAME_REQUIRED);
        }

        String folderName = request.normalizedFolderName();
        if (folderName.length() > MAX_FOLDER_NAME_LENGTH) {
            throw new FolderException(FolderErrorCode.FOLDER_NAME_TOO_LONG);
        }

        return folderName;
    }

    private Folder getOwnedFolder(
            Long userId,
            Long folderId
    ) {
        validateFolderId(folderId);

        return folderRepository.findByIdAndUser_Id(folderId, userId)
                .orElseThrow(() -> resolveFolderLookupException(folderId));
    }

    private RuntimeException resolveFolderLookupException(Long folderId) {
        if (folderRepository.existsById(folderId)) {
            return new FolderException(FolderErrorCode.FOLDER_ACCESS_DENIED);
        }

        return new FolderException(FolderErrorCode.FOLDER_NOT_FOUND);
    }

    private RuntimeException resolveTrashLookupException(
            Long userId,
            Long folderId
    ) {
        if (folderRepository.countByIdIncludingDeleted(folderId) == 0) {
            return new FolderException(FolderErrorCode.FOLDER_NOT_FOUND);
        }

        if (folderRepository.countByIdAndUserIdIncludingDeleted(folderId, userId) == 0) {
            return new FolderException(FolderErrorCode.FOLDER_ACCESS_DENIED);
        }

        if (folderRepository.countDeletedByIdAndUserId(folderId, userId) > 0) {
            return new FolderException(FolderErrorCode.FOLDER_ALREADY_DELETED);
        }

        return new FolderException(FolderErrorCode.FOLDER_NOT_FOUND);
    }

    private void validateRestorableFolder(
            Long userId,
            Long folderId
    ) {
        if (folderRepository.countByIdIncludingDeleted(folderId) == 0) {
            throw new FolderException(FolderErrorCode.FOLDER_NOT_FOUND);
        }

        if (folderRepository.countByIdAndUserIdIncludingDeleted(folderId, userId) == 0) {
            throw new FolderException(FolderErrorCode.FOLDER_ACCESS_DENIED);
        }

        if (folderRepository.countDeletedByIdAndUserId(folderId, userId) == 0) {
            throw new FolderException(FolderErrorCode.FOLDER_NOT_FOUND);
        }
    }

    private void validateFolderId(Long folderId) {
        if (folderId == null || folderId <= 0) {
            throw new FolderException(FolderErrorCode.INVALID_FOLDER_ID);
        }
    }
}
