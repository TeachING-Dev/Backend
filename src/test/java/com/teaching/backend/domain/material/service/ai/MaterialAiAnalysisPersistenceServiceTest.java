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

    // 재분석(#114)으로 태그가 새로 갱신될 때, 이번 분석에 더 이상 안 나오는 태그는 낡은 채로 남지 않고
    // 지워져야 한다. 여전히 나오는 태그는 다시 저장(중복)하지 않는다.
    @Test
    void removesStaleTagsNoLongerProducedByReanalysisButKeepsStillRelevantOnes() {
        Material material = material();
        ReflectionTestUtils.setField(material, "id", 100L);
        MaterialTag existingSpringTag = MaterialTag.create(material, Tag.create("spring"));
        MaterialTag existingJavaTag = MaterialTag.create(material, Tag.create("java"));
        MaterialAiAnalysisResult result = new MaterialAiAnalysisResult(
                "summary",
                "detail",
                List.of("spring"),
                null,
                null
        );
        when(materialAnalysisRepository.save(any(MaterialAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(100L)))
                .thenReturn(List.of(existingSpringTag, existingJavaTag));

        persistenceService.saveAnalysisResult(material, result);

        verify(materialTagRepository).deleteAll(List.of(existingJavaTag));
        verify(materialTagRepository, never()).save(any(MaterialTag.class));
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

    // 재분석(forceAnalyze=true)으로 같은 자료에 대해 다시 저장하면, material_id가 유니크 제약이라
    // 새로 insert하면 제약 위반이 난다(#114). 기존 분석이 있으면 그 자리에서 갱신해야 하고, 그때
    // 남아있는 이전 하이라이트도 새로 저장되는 것과 중복되지 않도록 먼저 지워야 한다.
    @Test
    void updatesExistingAnalysisInPlaceInsteadOfInsertingDuplicateWhenReanalyzing() {
        Material material = material();
        ReflectionTestUtils.setField(material, "id", 100L);
        MaterialAnalysis existingAnalysis = MaterialAnalysis.create(material, "old summary", "old detail", "v1");
        MaterialHighlight oldHighlight = MaterialHighlight.create(existingAnalysis, "old text", HighlightType.MAIN, 0, 5);
        MaterialAiAnalysisResult result = new MaterialAiAnalysisResult(
                "new summary",
                "new detail",
                List.of("spring"),
                null,
                null
        );
        when(materialAnalysisRepository.findByMaterialId(100L)).thenReturn(Optional.of(existingAnalysis));
        when(materialHighlightRepository.findAllByMaterialId(100L)).thenReturn(List.of(oldHighlight));
        when(tagRepository.findByName("spring")).thenReturn(Optional.of(Tag.create("spring")));

        MaterialAnalysis analysis = persistenceService.saveAnalysisResult(material, result);

        assertThat(analysis).isSameAs(existingAnalysis);
        assertThat(analysis.getSummary()).isEqualTo("new summary");
        assertThat(analysis.getDetailAnalysis()).isEqualTo("new detail");
        verify(materialAnalysisRepository, never()).save(any());
        verify(materialHighlightRepository).deleteAll(List.of(oldHighlight));
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
        Material material = Material.create(user, folder, "Title", "https://example.com", PlatformType.BLOG);
        // 실제로는 저장된(id가 채워진) Material만 saveAnalysisResult에 넘어온다 — saveTags가
        // material.getId()를 List.of(...)에 담는데, null이면 NPE가 난다.
        ReflectionTestUtils.setField(material, "id", 999L);
        return material;
    }
}
