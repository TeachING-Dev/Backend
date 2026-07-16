package com.teaching.backend.domain.folder.service;

import com.teaching.backend.domain.folder.dto.request.FolderCreateRequest;
import com.teaching.backend.domain.folder.dto.request.FolderRenameRequest;
import com.teaching.backend.domain.folder.dto.response.FolderCreateResponse;
import com.teaching.backend.domain.folder.dto.response.FolderDetailResponse;
import com.teaching.backend.domain.folder.dto.response.FolderListResponse;
import com.teaching.backend.domain.folder.dto.response.FolderMaterialItemResponse;
import com.teaching.backend.domain.folder.dto.response.FolderMaterialListResponse;
import com.teaching.backend.domain.folder.dto.response.FolderRestoreResponse;
import com.teaching.backend.domain.folder.dto.response.FolderRenameResponse;
import com.teaching.backend.domain.folder.dto.response.FolderTrashResponse;
import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.UserRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FolderService {

    private static final String DEFAULT_SORT = "recent";
    private static final int MAX_FOLDER_COUNT = 6;
    private static final int MAX_FOLDER_NAME_LENGTH = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialTagRepository materialTagRepository;

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

    public FolderMaterialListResponse getFolderMaterials(
            Long userId,
            Long folderId,
            String keyword,
            String sort,
            Integer page,
            Integer size
    ) {
        Folder folder = getOwnedFolder(userId, folderId);
        String normalizedKeyword = normalizeKeyword(keyword);
        PageRequest pageRequest = PageRequest.of(
                resolvePage(page),
                resolveSize(size),
                resolveMaterialSort(sort)
        );

        Page<Material> materialPage = materialRepository.searchFolderMaterials(
                folderId,
                userId,
                normalizedKeyword,
                pageRequest
        );

        List<FolderMaterialItemResponse> content = createMaterialItemResponses(materialPage.getContent());

        return FolderMaterialListResponse.of(folder, materialPage, content);
    }

    @Transactional
    public FolderCreateResponse createFolder(
            Long userId,
            FolderCreateRequest request
    ) {
        String folderName = validateAndNormalizeFolderName(request);

        User user = lockUserForFolderMutation(userId);

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
        lockUserForFolderMutation(userId);

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

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    private Sort resolveMaterialSort(String sort) {
        String normalizedSort = normalizeSort(sort);

        return switch (normalizedSort) {
            case "recent" ->
                    Sort.by(
                            Sort.Order.desc("createdAt"),
                            Sort.Order.desc("id")
                    );

            case "oldest" ->
                    Sort.by(
                            Sort.Order.asc("createdAt"),
                            Sort.Order.asc("id")
                    );

            case "title" ->
                    Sort.by(
                            Sort.Order.asc("title"),
                            Sort.Order.asc("id")
                    );

            default -> throw new FolderException(FolderErrorCode.INVALID_SORT);
        };
    }

    private int resolvePage(Integer page) {
        if (page == null) {
            return 0;
        }

        if (page < 0) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return 10;
        }

        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        return size;
    }

    private List<FolderMaterialItemResponse> createMaterialItemResponses(List<Material> materials) {
        if (materials.isEmpty()) {
            return List.of();
        }

        List<Long> materialIds = materials.stream()
                .map(Material::getId)
                .toList();

        Map<Long, String> summaryByMaterialId = getSummaryByMaterialId(materialIds);
        Map<Long, List<String>> tagsByMaterialId = getTagsByMaterialId(materialIds);

        return materials.stream()
                .map(material -> FolderMaterialItemResponse.of(
                        material,
                        summaryByMaterialId.get(material.getId()),
                        tagsByMaterialId.getOrDefault(material.getId(), List.of())
                ))
                .toList();
    }

    private Map<Long, String> getSummaryByMaterialId(List<Long> materialIds) {
        return materialAnalysisRepository.findAllActiveByMaterialIds(materialIds)
                .stream()
                .collect(Collectors.toMap(
                        analysis -> analysis.getMaterial().getId(),
                        MaterialAnalysis::getSummary,
                        (current, ignored) -> current
                ));
    }

    private Map<Long, List<String>> getTagsByMaterialId(List<Long> materialIds) {
        Map<Long, List<String>> tagsByMaterialId = new HashMap<>();

        for (MaterialTag materialTag : materialTagRepository.findAllWithTagByMaterialIds(materialIds)) {
            tagsByMaterialId
                    .computeIfAbsent(materialTag.getMaterial().getId(), ignored -> new ArrayList<>())
                    .add(materialTag.getTag().getName());
        }

        return tagsByMaterialId;
    }

    private User lockUserForFolderMutation(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
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
