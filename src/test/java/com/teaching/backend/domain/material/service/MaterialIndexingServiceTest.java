package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.material.dto.indexing.EmbeddedMaterialTextChunk;
import com.teaching.backend.domain.material.dto.indexing.MaterialTextChunk;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialChunkRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.material.service.indexing.MaterialEmbeddingService;
import com.teaching.backend.domain.material.service.indexing.MaterialTextChunker;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.ai.qdrant.QdrantClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialIndexingServiceTest {

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialAnalysisRepository materialAnalysisRepository;

    @Mock
    private MaterialChunkRepository materialChunkRepository;

    @Mock
    private MaterialTextChunker materialTextChunker;

    @Mock
    private MaterialEmbeddingService materialEmbeddingService;

    @Mock
    private QdrantClient qdrantClient;

    @InjectMocks
    private MaterialIndexingService indexingService;

    @Test
    void indexesMaterialAnalysisWithStablePointIdAndPayload() {
        Material material = material();
        MaterialTextChunk textChunk = new MaterialTextChunk(0, "chunk text", "청크 1");
        when(materialTextChunker.chunk("source text")).thenReturn(List.of(textChunk));
        when(materialEmbeddingService.embedChunks(List.of(textChunk)))
                .thenReturn(List.of(new EmbeddedMaterialTextChunk(textChunk, new float[]{0.1f, 0.2f})));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(100L)).thenReturn(List.of());
        when(materialChunkRepository.save(any(MaterialChunk.class))).thenAnswer(invocation -> {
            MaterialChunk chunk = invocation.getArgument(0);
            ReflectionTestUtils.setField(chunk, "id", 300L);
            return chunk;
        });

        int chunkCount = indexingService.indexMaterialContent(material, "source text");

        assertThat(chunkCount).isEqualTo(1);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(qdrantClient).upsertPoint(eq("material-100-chunk-0"), any(float[].class), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("materialChunkId", 300L)
                .containsEntry("materialId", 100L)
                .containsEntry("userId", 1L)
                .containsEntry("folderId", 10L)
                .containsEntry("chunkIndex", 0)
                .containsEntry("text", "chunk text")
                .containsEntry("materialTitle", "Title")
                .containsEntry("originalUrl", "https://example.com");
    }

    @Test
    void reindexReusesExistingChunkAndDeletesExcessChunks() {
        Material material = material();
        MaterialTextChunk textChunk = new MaterialTextChunk(0, "new text", "청크 1");
        MaterialChunk first = chunk(material, 0, "old text", "material-100-chunk-0", 300L);
        MaterialChunk excess = chunk(material, 1, "old extra", "material-100-chunk-1", 301L);
        when(materialTextChunker.chunk("source text")).thenReturn(List.of(textChunk));
        when(materialEmbeddingService.embedChunks(List.of(textChunk)))
                .thenReturn(List.of(new EmbeddedMaterialTextChunk(textChunk, new float[]{0.1f})));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(100L)).thenReturn(List.of(first, excess));

        indexingService.indexMaterialContent(material, "source text");

        assertThat(first.getChunkText()).isEqualTo("new text");
        verify(materialChunkRepository, never()).save(any(MaterialChunk.class));
        verify(qdrantClient).deletePoints(List.of("material-100-chunk-1"));
        verify(materialChunkRepository).deleteAll(List.of(excess));
    }

    @Test
    void emptyTextFailsBeforeEmbeddingAndQdrant() {
        Material material = material();
        when(materialTextChunker.chunk(" ")).thenReturn(List.of());

        assertThatThrownBy(() -> indexingService.indexMaterialContent(material, " "))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_INDEXING_TEXT_EMPTY);
        verify(materialEmbeddingService, never()).embedChunks(any());
        verify(qdrantClient, never()).ensureCollection();
    }

    @Test
    void qdrantFailureIsConvertedToMaterialException() {
        Material material = material();
        MaterialTextChunk textChunk = new MaterialTextChunk(0, "chunk text", "청크 1");
        when(materialTextChunker.chunk("source text")).thenReturn(List.of(textChunk));
        when(materialEmbeddingService.embedChunks(List.of(textChunk)))
                .thenReturn(List.of(new EmbeddedMaterialTextChunk(textChunk, new float[]{0.1f})));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(100L)).thenReturn(List.of());
        when(materialChunkRepository.save(any(MaterialChunk.class))).thenAnswer(invocation -> {
            MaterialChunk chunk = invocation.getArgument(0);
            ReflectionTestUtils.setField(chunk, "id", 300L);
            return chunk;
        });
        org.mockito.Mockito.doThrow(new RuntimeException("qdrant"))
                .when(qdrantClient)
                .upsertPoint(eq("material-100-chunk-0"), any(float[].class), any());

        assertThatThrownBy(() -> indexingService.indexMaterialContent(material, "source text"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
    }

    @Test
    void manualIndexApiKeepsOwnershipAndUsesAnalysisText() {
        Material material = material();
        MaterialAnalysis analysis = MaterialAnalysis.create(material, "summary", "detail text", "v1");
        MaterialTextChunk textChunk = new MaterialTextChunk(0, "detail text", "청크 1");
        when(materialRepository.findById(100L)).thenReturn(Optional.of(material));
        when(materialAnalysisRepository.findByMaterialId(100L)).thenReturn(Optional.of(analysis));
        when(materialTextChunker.chunk("detail text")).thenReturn(List.of(textChunk));
        when(materialEmbeddingService.embedChunks(List.of(textChunk)))
                .thenReturn(List.of(new EmbeddedMaterialTextChunk(textChunk, new float[]{0.1f})));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(100L)).thenReturn(List.of());
        when(materialChunkRepository.save(any(MaterialChunk.class))).thenAnswer(invocation -> {
            MaterialChunk chunk = invocation.getArgument(0);
            ReflectionTestUtils.setField(chunk, "id", 300L);
            return chunk;
        });

        int chunkCount = indexingService.indexMaterial(100L, 1L);

        assertThat(chunkCount).isEqualTo(1);
        verify(materialTextChunker).chunk("detail text");
    }

    private Material material() {
        User user = User.create("user@example.com", "user", null, null, null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Folder folder = Folder.create(user, "Folder");
        ReflectionTestUtils.setField(folder, "id", 10L);
        Material material = Material.create(user, folder, "Title", "https://example.com", PlatformType.BLOG);
        ReflectionTestUtils.setField(material, "id", 100L);
        return material;
    }

    private MaterialChunk chunk(Material material, int index, String text, String pointId, Long id) {
        MaterialChunk chunk = MaterialChunk.create(material, index, text, pointId, "청크 " + (index + 1));
        ReflectionTestUtils.setField(chunk, "id", id);
        return chunk;
    }
}
