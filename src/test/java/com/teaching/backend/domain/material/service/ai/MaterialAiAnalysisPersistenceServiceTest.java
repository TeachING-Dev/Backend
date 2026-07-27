package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.material.dto.ai.MaterialAiHighlightResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.entity.MaterialHighlight;
import com.teaching.backend.domain.material.enums.HighlightType;
import com.teaching.backend.domain.material.enums.MaterialAiHighlightType;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialHighlightRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialAiAnalysisPersistenceServiceTest {

    @Mock
    private MaterialAnalysisRepository materialAnalysisRepository;

    @Mock
    private MaterialHighlightRepository materialHighlightRepository;

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
                null,
                null
        );
        when(materialAnalysisRepository.save(any(MaterialAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByName("spring")).thenReturn(Optional.of(existingTag));
        when(tagRepository.findByName("jpa")).thenReturn(Optional.of(Tag.create("jpa")));

        MaterialAnalysis analysis = persistenceService.saveAnalysisResult(material, result);

        assertThat(analysis.getSummary()).isEqualTo("summary");
        verify(tagRepository).insertIfAbsent("spring");
        verify(tagRepository).insertIfAbsent("jpa");
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
                null,
                null
        );
        when(materialAnalysisRepository.save(any(MaterialAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByName("spring")).thenReturn(Optional.of(newTag));

        persistenceService.saveAnalysisResult(material, result);

        verify(tagRepository).insertIfAbsent("spring");
        verify(tagRepository).findByName("spring");
        verify(materialTagRepository).save(any(MaterialTag.class));
    }

    @Test
    void throwsWhenTagCannotBeReadAfterAtomicInsert() {
        Material material = material();
        MaterialAiAnalysisResult result = new MaterialAiAnalysisResult(
                "summary",
                "detail",
                List.of("spring"),
                null,
                null
        );
        when(materialAnalysisRepository.save(any(MaterialAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByName("spring")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> persistenceService.saveAnalysisResult(material, result))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("spring");

        verify(tagRepository).insertIfAbsent("spring");
        verify(tagRepository).findByName("spring");
        verify(materialTagRepository, never()).save(any());
    }

    @Test
    void ignoresBlankAndTooLongTagNames() {
        Material material = material();
        String tooLong = "a".repeat(51);
        MaterialAiAnalysisResult result = new MaterialAiAnalysisResult(
                "summary",
                "detail",
                List.of("", "  ", tooLong),
                null,
                null
        );
        when(materialAnalysisRepository.save(any(MaterialAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));

        persistenceService.saveAnalysisResult(material, result);

        verify(tagRepository, never()).findByName(any());
        verify(tagRepository, never()).insertIfAbsent(any());
        verify(materialTagRepository, never()).save(any());
    }

    @Test
    void savesValidatedHighlightsWithOffsetsAndNormalizedType() {
        Material material = material();
        ReflectionTestUtils.setField(material, "id", 100L);
        String longAnalysis = "## Summary\n* **Main** point\nImportant sentence. Be careful.";
        MaterialAnalysis analysis = MaterialAnalysis.create(material, "summary", longAnalysis, "v1");
        MaterialAiHighlightResult core = new MaterialAiHighlightResult("Important sentence.", MaterialAiHighlightType.CORE);
        MaterialAiHighlightResult caution = new MaterialAiHighlightResult("Be careful.", MaterialAiHighlightType.CAUTION);
        when(materialHighlightRepository.save(any(MaterialHighlight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        persistenceService.saveHighlights(analysis, List.of(core, caution));

        ArgumentCaptor<MaterialHighlight> captor = ArgumentCaptor.forClass(MaterialHighlight.class);
        verify(materialHighlightRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getHighlightText()).isEqualTo("Important sentence.");
        assertThat(captor.getAllValues().get(0).getHighlightType()).isEqualTo(HighlightType.MAIN);
        assertThat(captor.getAllValues().get(0).getStartPosition()).isEqualTo(longAnalysis.indexOf("Important sentence."));
        assertThat(captor.getAllValues().get(0).getMaterialAnalysis()).isSameAs(analysis);
        assertThat(longAnalysis.substring(
                captor.getAllValues().get(0).getStartPosition(),
                captor.getAllValues().get(0).getEndPosition()
        )).isEqualTo("Important sentence.");
        assertThat(captor.getAllValues().get(1).getHighlightType()).isEqualTo(HighlightType.CAUTION);
    }

    private Material material() {
        User user = User.create("user@example.com", "user", null, null, null);
        Folder folder = Folder.create(user, "Folder");
        return Material.create(user, folder, "Title", "https://example.com", PlatformType.BLOG);
    }
}
