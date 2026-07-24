package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.service.FolderService;
import com.teaching.backend.domain.material.dto.request.MaterialAnalyzeRequest;
import com.teaching.backend.domain.material.dto.response.MaterialAnalyzeResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.MaterialAnalyzeResultType;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialUrlAnalysisServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FOLDER_ID = 10L;
    private static final String URL = "https://velog.io/@example/spring";

    @Mock
    private FolderService folderService;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialUrlValidator materialUrlValidator;

    @Mock
    private MaterialPlatformResolver materialPlatformResolver;

    @InjectMocks
    private MaterialUrlAnalysisService materialUrlAnalysisService;

    @Test
    void returnsAlreadyAnalyzedWhenCompletedMaterialExistsAndForceAnalyzeFalse() {
        Material newerFailed = material(102L, "Failed", URL, PlatformType.VELOG, AiStatus.FAILED, createdAt(2));
        Material olderCompleted = material(101L, "Completed", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(1));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(newerFailed, olderCompleted));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ALREADY_ANALYZED);
        assertThat(result.existingMaterialId()).isEqualTo(101L);
        assertThat(result.title()).isEqualTo("Completed");
        assertThat(result.originalUrl()).isEqualTo(URL);
        assertThat(result.platformType()).isEqualTo("VELOG");
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    void selectsLatestCompletedMaterialWhenDuplicatesExist() {
        Material olderCompleted = material(101L, "Older", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(1));
        Material newerCompleted = material(102L, "Newer", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(2));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(olderCompleted, newerCompleted));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        );

        assertThat(result.existingMaterialId()).isEqualTo(102L);
        assertThat(result.title()).isEqualTo("Newer");
    }

    @Test
    void forceAnalyzeTrueReturnsAnalysisRequiredWithoutReusingExistingMaterial() {
        Material completed = material(101L, "Completed", URL, PlatformType.VELOG, AiStatus.COMPLETED, createdAt(1));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(completed));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, true)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_REQUIRED);
        assertThat(result.existingMaterialId()).isNull();
        assertThat(result.originalUrl()).isEqualTo(URL);
        assertThat(result.platformType()).isEqualTo("VELOG");
        assertThat(result.status()).isNull();
    }

    @Test
    void returnsAnalysisRequiredWhenNoCompletedMaterialExists() {
        Material failed = material(101L, "Failed", URL, PlatformType.VELOG, AiStatus.FAILED, createdAt(1));
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of(failed));

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_REQUIRED);
        assertThat(result.existingMaterialId()).isNull();
    }

    @Test
    void trimsUrlBeforeValidationResolveAndDuplicateLookup() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());

        MaterialAnalyzeResponse result = materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest("  " + URL + "  ", FOLDER_ID, null)
        );

        assertThat(result.resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_REQUIRED);
        assertThat(result.originalUrl()).isEqualTo(URL);
        verify(materialUrlValidator).isValidHttpUrl(URL);
        verify(materialPlatformResolver).resolve(null, URL);
        verify(materialRepository).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL);
    }

    @Test
    void validatesOwnedFolderBeforeDuplicateLookup() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());

        materialUrlAnalysisService.analyze(USER_ID, new MaterialAnalyzeRequest(URL, FOLDER_ID, false));

        verify(folderService).getOwnedFolder(USER_ID, FOLDER_ID);
    }

    @Test
    void propagatesFolderNotFoundFromFolderService() {
        when(materialUrlValidator.isValidHttpUrl(URL)).thenReturn(true);
        when(folderService.getOwnedFolder(USER_ID, FOLDER_ID))
                .thenThrow(new FolderException(FolderErrorCode.FOLDER_NOT_FOUND));

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        ))
                .isInstanceOf(FolderException.class)
                .extracting("errorCode")
                .isEqualTo(FolderErrorCode.FOLDER_NOT_FOUND);
        verify(materialRepository, never()).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(any(), anyString());
    }

    @Test
    void propagatesFolderAccessDeniedFromFolderService() {
        when(materialUrlValidator.isValidHttpUrl(URL)).thenReturn(true);
        when(folderService.getOwnedFolder(USER_ID, FOLDER_ID))
                .thenThrow(new FolderException(FolderErrorCode.FOLDER_ACCESS_DENIED));

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, FOLDER_ID, false)
        ))
                .isInstanceOf(FolderException.class)
                .extracting("errorCode")
                .isEqualTo(FolderErrorCode.FOLDER_ACCESS_DENIED);
    }

    @Test
    void rejectsInvalidFolderId() {
        when(materialUrlValidator.isValidHttpUrl(URL)).thenReturn(true);

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(URL, 0L, false)
        ))
                .isInstanceOf(FolderException.class)
                .extracting("errorCode")
                .isEqualTo(FolderErrorCode.INVALID_FOLDER_ID);
        verify(folderService, never()).getOwnedFolder(any(), any());
    }

    @Test
    void rejectsNullUrlAsOriginalUrlRequired() {
        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest(null, FOLDER_ID, false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.ORIGINAL_URL_REQUIRED);
        verify(materialUrlValidator, never()).isValidHttpUrl(anyString());
    }

    @Test
    void rejectsBlankUrlAsOriginalUrlRequired() {
        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest("   ", FOLDER_ID, false)
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.ORIGINAL_URL_REQUIRED);
    }

    @Test
    void rejectsInvalidHttpUrlAsBadRequestBeforeFolderAndDuplicateLookup() {
        when(materialUrlValidator.isValidHttpUrl("ftp://example.com")).thenReturn(false);

        assertThatThrownBy(() -> materialUrlAnalysisService.analyze(
                USER_ID,
                new MaterialAnalyzeRequest("ftp://example.com", FOLDER_ID, false)
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.BAD_REQUEST);
        verify(folderService, never()).getOwnedFolder(any(), any());
        verify(materialPlatformResolver, never()).resolve(any(), anyString());
        verify(materialRepository, never()).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(any(), anyString());
    }

    @Test
    void repositoryLookupUsesCurrentUserId() {
        givenValidRequest();
        when(materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(USER_ID, URL))
                .thenReturn(List.of());

        materialUrlAnalysisService.analyze(USER_ID, new MaterialAnalyzeRequest(URL, FOLDER_ID, false));

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(materialRepository).findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(
                userIdCaptor.capture(),
                anyString()
        );
        assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
    }

    private void givenValidRequest() {
        when(materialUrlValidator.isValidHttpUrl(URL)).thenReturn(true);
        when(folderService.getOwnedFolder(USER_ID, FOLDER_ID)).thenReturn(folder(USER_ID, FOLDER_ID));
        when(materialPlatformResolver.resolve(null, URL)).thenReturn(PlatformType.VELOG);
    }

    private Material material(
            Long materialId,
            String title,
            String originalUrl,
            PlatformType platformType,
            AiStatus aiStatus,
            LocalDateTime createdAt
    ) {
        User user = user(USER_ID);
        Folder folder = folder(user.getId(), FOLDER_ID);
        Material material = Material.create(user, folder, title, originalUrl, platformType);
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

    private User user(Long userId) {
        User user = User.create("user" + userId + "@example.com", "user" + userId, null, null, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private LocalDateTime createdAt(int day) {
        return LocalDateTime.of(2026, 7, day, 10, 0);
    }
}
