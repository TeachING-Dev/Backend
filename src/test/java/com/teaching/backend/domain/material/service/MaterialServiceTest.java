package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.dto.MaterialListResponse;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.dto.request.MaterialAnalysisGenerateRequest;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.entity.Tag;
import com.teaching.backend.domain.tag.exception.TagErrorCode;
import com.teaching.backend.domain.tag.exception.TagException;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.tag.repository.TagRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.ai.openai.OpenAiClient;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private OpenAiClient openAiClient;

    @Mock
    private MaterialAnalysisPromptBuilder materialAnalysisPromptBuilder;

    @Mock
    private MaterialAiAnalysisResponseParser materialAiAnalysisResponseParser;

    @Mock
    private MaterialPlatformResolver materialPlatformResolver;

    @Mock
    private MaterialUrlValidator materialUrlValidator;

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
        verifyNoInteractions(tagRepository);
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

    @Test
    void generateMaterialWithAnalysisAcceptsHttpsUrlAndStoresTrimmedUrl() {
        MaterialAnalysisGenerateRequest request = new MaterialAnalysisGenerateRequest(
                "Title",
                "  https://example.com/article  ",
                "content",
                null
        );
        givenSuccessfulAnalysis("https://example.com/article", PlatformType.WEB);

        materialService.generateMaterialWithAnalysis(USER_ID, FOLDER_ID, request);

        ArgumentCaptor<Material> materialCaptor = ArgumentCaptor.forClass(Material.class);
        verify(materialRepository).save(materialCaptor.capture());
        assertThat(materialCaptor.getValue().getOriginalUrl()).isEqualTo("https://example.com/article");
        verify(materialUrlValidator).isValidHttpUrl("https://example.com/article");
        verify(materialPlatformResolver).resolve(null, "https://example.com/article");
    }

    @Test
    void generateMaterialWithAnalysisAcceptsHttpUrl() {
        MaterialAnalysisGenerateRequest request = new MaterialAnalysisGenerateRequest(
                "Title",
                "http://example.com/article",
                "content",
                null
        );
        givenSuccessfulAnalysis("http://example.com/article", PlatformType.WEB);

        assertThatCode(() -> materialService.generateMaterialWithAnalysis(USER_ID, FOLDER_ID, request))
                .doesNotThrowAnyException();
    }

    @Test
    void generateMaterialWithAnalysisRejectsInvalidUrl() {
        MaterialAnalysisGenerateRequest request = new MaterialAnalysisGenerateRequest(
                "Title",
                "ftp://example.com",
                "content",
                null
        );
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder(USER_ID, FOLDER_ID)));
        when(materialUrlValidator.isValidHttpUrl("ftp://example.com")).thenReturn(false);

        assertBadRequestThrown(() -> materialService.generateMaterialWithAnalysis(USER_ID, FOLDER_ID, request));
        verify(materialRepository, never()).save(any(Material.class));
        verify(materialPlatformResolver, never()).resolve(any(), any());
        verify(openAiClient, never()).chatCompleteJson(any(), any());
    }

    @Test
    void generateMaterialWithAnalysisRejectsUrlWithoutScheme() {
        MaterialAnalysisGenerateRequest request = new MaterialAnalysisGenerateRequest(
                "Title",
                "example.com",
                "content",
                null
        );
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder(USER_ID, FOLDER_ID)));
        when(materialUrlValidator.isValidHttpUrl("example.com")).thenReturn(false);

        assertBadRequestThrown(() -> materialService.generateMaterialWithAnalysis(USER_ID, FOLDER_ID, request));
        verify(materialRepository, never()).save(any(Material.class));
        verify(materialPlatformResolver, never()).resolve(any(), any());
        verify(openAiClient, never()).chatCompleteJson(any(), any());
    }

    @Test
    void generateMaterialWithAnalysisRejectsUrlWithoutHost() {
        MaterialAnalysisGenerateRequest request = new MaterialAnalysisGenerateRequest(
                "Title",
                "https:///article",
                "content",
                null
        );
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder(USER_ID, FOLDER_ID)));
        when(materialUrlValidator.isValidHttpUrl("https:///article")).thenReturn(false);

        assertBadRequestThrown(() -> materialService.generateMaterialWithAnalysis(USER_ID, FOLDER_ID, request));
        verify(materialRepository, never()).save(any(Material.class));
        verify(materialPlatformResolver, never()).resolve(any(), any());
        verify(openAiClient, never()).chatCompleteJson(any(), any());
    }

    @Test
    void generateMaterialWithAnalysisRejectsNullUrlAsOriginalUrlRequired() {
        MaterialAnalysisGenerateRequest request = new MaterialAnalysisGenerateRequest(
                "Title",
                null,
                "content",
                null
        );
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder(USER_ID, FOLDER_ID)));

        assertMaterialExceptionThrown(
                () -> materialService.generateMaterialWithAnalysis(USER_ID, FOLDER_ID, request),
                MaterialErrorCode.ORIGINAL_URL_REQUIRED
        );
        verify(materialUrlValidator, never()).isValidHttpUrl(any());
        verify(materialRepository, never()).save(any(Material.class));
        verify(materialPlatformResolver, never()).resolve(any(), any());
        verify(openAiClient, never()).chatCompleteJson(any(), any());
    }

    @Test
    void generateMaterialWithAnalysisRejectsBlankUrlAsOriginalUrlRequired() {
        MaterialAnalysisGenerateRequest request = new MaterialAnalysisGenerateRequest(
                "Title",
                "   ",
                "content",
                null
        );
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder(USER_ID, FOLDER_ID)));

        assertMaterialExceptionThrown(
                () -> materialService.generateMaterialWithAnalysis(USER_ID, FOLDER_ID, request),
                MaterialErrorCode.ORIGINAL_URL_REQUIRED
        );
        verify(materialUrlValidator, never()).isValidHttpUrl(any());
        verify(materialRepository, never()).save(any(Material.class));
        verify(materialPlatformResolver, never()).resolve(any(), any());
        verify(openAiClient, never()).chatCompleteJson(any(), any());
    }

    private void assertTagExceptionThrown(Runnable action, TagErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(TagException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private void assertMaterialExceptionThrown(Runnable action, MaterialErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private void assertBadRequestThrown(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.BAD_REQUEST);
    }

    private void givenSuccessfulAnalysis(String originalUrl, PlatformType platformType) {
        Folder folder = folder(USER_ID, FOLDER_ID);
        when(folderRepository.findByIdAndUser_Id(FOLDER_ID, USER_ID)).thenReturn(Optional.of(folder));
        when(materialUrlValidator.isValidHttpUrl(originalUrl)).thenReturn(true);
        when(materialPlatformResolver.resolve(null, originalUrl)).thenReturn(platformType);
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(materialAnalysisPromptBuilder.buildSystemPrompt(USER_ID)).thenReturn("system");
        when(materialAnalysisPromptBuilder.buildUserMessage(originalUrl, "content")).thenReturn("user");
        when(openAiClient.chatCompleteJson("system", "user")).thenReturn("raw");
        when(materialAiAnalysisResponseParser.parse("raw")).thenReturn(
                new MaterialAiAnalysisResult("summary", "detail", List.of(), null, null)
        );
        when(materialAnalysisRepository.save(any(MaterialAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

    private User user(Long userId) {
        User user = User.create("user" + userId + "@example.com", "user" + userId, null, null, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private LocalDateTime createdAt(int day) {
        return LocalDateTime.of(2026, 7, day, 10, 0);
    }
}
