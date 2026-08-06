package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.entity.Tag;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.tag.repository.TagRepository;
import com.teaching.backend.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialAiAnalysisPersistenceServiceTest {

    @Mock
    private MaterialAnalysisRepository materialAnalysisRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private MaterialTagRepository materialTagRepository;

    @InjectMocks
    private MaterialAiAnalysisPersistenceService persistenceService;

    @Test
    void savesMaterialAnalysisAndReusesExistingTag() {
        Material material = material();
        Tag existingTag = Tag.create("spring");
        MaterialAiAnalysisResult result = new MaterialAiAnalysisResult(
                "summary",
                "detail",
                List.of(" spring ", "spring", "jpa"),
                null
        );
        when(materialAnalysisRepository.save(any(MaterialAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByName("spring")).thenReturn(Optional.of(existingTag));
        when(tagRepository.findByName("jpa")).thenReturn(Optional.of(Tag.create("jpa")));

        MaterialAnalysis analysis = persistenceService.saveAnalysisResult(material, result);

        assertThat(analysis.getSummary()).isEqualTo("summary");
        verify(tagRepository, never()).insertIfAbsent("spring");
        verify(tagRepository, never()).insertIfAbsent("jpa");
        verify(tagRepository).findByName("spring");
        verify(tagRepository).findByName("jpa");
        ArgumentCaptor<MaterialTag> materialTagCaptor = ArgumentCaptor.forClass(MaterialTag.class);
        verify(materialTagRepository, times(2)).save(materialTagCaptor.capture());
        assertThat(materialTagCaptor.getAllValues()).hasSize(2);
    }

    @Test
    void savesMaterialAnalysisAndCreatesNewTagWithAtomicInsert() {
        Material material = material();
        Tag newTag = Tag.create("spring");
        MaterialAiAnalysisResult result = new MaterialAiAnalysisResult(
                "summary",
                "detail",
                List.of("spring"),
                null
        );
        when(materialAnalysisRepository.save(any(MaterialAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByName("spring"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(newTag));

        persistenceService.saveAnalysisResult(material, result);

        verify(tagRepository).insertIfAbsent("spring");
        verify(tagRepository, times(2)).findByName("spring");
        verify(materialTagRepository).save(any(MaterialTag.class));
    }

    @Test
    void throwsWhenTagCannotBeReadAfterAtomicInsert() {
        Material material = material();
        MaterialAiAnalysisResult result = new MaterialAiAnalysisResult(
                "summary",
                "detail",
                List.of("spring"),
                null
        );
        when(materialAnalysisRepository.save(any(MaterialAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByName("spring")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> persistenceService.saveAnalysisResult(material, result))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("spring");

        verify(tagRepository).insertIfAbsent("spring");
        verify(tagRepository, atLeastOnce()).findByName("spring");
        verify(materialTagRepository, never()).save(any());
    }

    @Test
    void ignoresBlankAndTruncatesTooLongTagNames() {
        Material material = material();
        String tooLong = "a".repeat(51);
        String truncated = "a".repeat(50);
        Tag truncatedTag = Tag.create(truncated);
        MaterialAiAnalysisResult result = new MaterialAiAnalysisResult(
                "summary",
                "detail",
                List.of("", "  ", tooLong),
                null
        );
        when(materialAnalysisRepository.save(any(MaterialAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByName(truncated))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(truncatedTag));

        persistenceService.saveAnalysisResult(material, result);

        verify(tagRepository, never()).findByName("");
        verify(tagRepository, never()).findByName("  ");
        verify(tagRepository).insertIfAbsent(truncated);
        verify(materialTagRepository).save(any(MaterialTag.class));
    }

    private Material material() {
        User user = User.create("user@example.com", "user", null, null, null);
        Folder folder = Folder.create(user, "Folder");
        return Material.create(user, folder, "Title", "https://example.com", PlatformType.BLOG);
    }
}
