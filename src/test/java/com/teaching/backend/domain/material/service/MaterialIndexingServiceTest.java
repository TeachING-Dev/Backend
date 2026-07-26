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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        String expectedPointId = MaterialIndexingService.qdrantPointIdOf(100L, 0);
        UUID.fromString(expectedPointId);
        verify(qdrantClient).upsertPoint(eq(expectedPointId), any(float[].class), payloadCaptor.capture());
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
    void pointIdIsDeterministicUuid() {
        String first = MaterialIndexingService.qdrantPointIdOf(100L, 0);
        String second = MaterialIndexingService.qdrantPointIdOf(100L, 0);
        String differentChunk = MaterialIndexingService.qdrantPointIdOf(100L, 1);
        String differentMaterial = MaterialIndexingService.qdrantPointIdOf(101L, 0);

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(differentChunk);
        assertThat(first).isNotEqualTo(differentMaterial);
        assertThatCode(() -> UUID.fromString(first)).doesNotThrowAnyException();
        assertThatCode(() -> UUID.fromString(differentChunk)).doesNotThrowAnyException();
        assertThatCode(() -> UUID.fromString(differentMaterial)).doesNotThrowAnyException();
    }

    @Test
    void reindexReusesExistingChunkAndDeletesExcessChunks() {
        Material material = material();
        MaterialTextChunk textChunk = new MaterialTextChunk(0, "new text", "청크 1");
        String firstPointId = MaterialIndexingService.qdrantPointIdOf(100L, 0);
        String excessPointId = MaterialIndexingService.qdrantPointIdOf(100L, 1);
        MaterialChunk first = chunk(material, 0, "old text", firstPointId, 300L);
        MaterialChunk excess = chunk(material, 1, "old extra", excessPointId, 301L);
        when(materialTextChunker.chunk("source text")).thenReturn(List.of(textChunk));
        when(materialEmbeddingService.embedChunks(List.of(textChunk)))
                .thenReturn(List.of(new EmbeddedMaterialTextChunk(textChunk, new float[]{0.1f})));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(100L)).thenReturn(List.of(first, excess));

        indexingService.indexMaterialContent(material, "source text");

        ArgumentCaptor<String> upsertedPointId = ArgumentCaptor.forClass(String.class);
        verify(qdrantClient).upsertPoint(upsertedPointId.capture(), any(float[].class), any());
        String newPointId = upsertedPointId.getValue();
        assertThatCode(() -> UUID.fromString(newPointId)).doesNotThrowAnyException();
        assertThat(newPointId).isNotEqualTo(firstPointId);
        assertThat(first.getChunkText()).isEqualTo("new text");
        assertThat(first.getQdrantPointId()).isEqualTo(newPointId);
        verify(materialChunkRepository, never()).save(any(MaterialChunk.class));
        ArgumentCaptor<List<String>> deletedPointIds = ArgumentCaptor.forClass(List.class);
        verify(qdrantClient).deletePoints(deletedPointIds.capture());
        assertThat(deletedPointIds.getValue()).containsExactlyInAnyOrder(firstPointId, excessPointId);
        verify(materialChunkRepository).deleteAll(List.of(excess));
    }

    @Test
    void repeatedSameContentReindexUsesFreshStagedPointIds() {
        Material material = material();
        MaterialTextChunk textChunk = new MaterialTextChunk(0, "same text", "chunk 1");
        String initialPointId = MaterialIndexingService.qdrantPointIdOf(100L, 0);
        MaterialChunk existingChunk = chunk(material, 0, "same text", initialPointId, 300L);
        when(materialTextChunker.chunk("source text")).thenReturn(List.of(textChunk));
        when(materialEmbeddingService.embedChunks(List.of(textChunk)))
                .thenReturn(List.of(new EmbeddedMaterialTextChunk(textChunk, new float[]{0.1f})));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(100L))
                .thenReturn(List.of(existingChunk));

        indexingService.indexMaterialContent(material, "source text");
        indexingService.indexMaterialContent(material, "source text");

        ArgumentCaptor<String> upsertedPointIds = ArgumentCaptor.forClass(String.class);
        verify(qdrantClient, times(2)).upsertPoint(upsertedPointIds.capture(), any(float[].class), any());
        String firstReindexPointId = upsertedPointIds.getAllValues().get(0);
        String secondReindexPointId = upsertedPointIds.getAllValues().get(1);
        assertThatCode(() -> UUID.fromString(firstReindexPointId)).doesNotThrowAnyException();
        assertThatCode(() -> UUID.fromString(secondReindexPointId)).doesNotThrowAnyException();
        assertThat(firstReindexPointId).isNotEqualTo(initialPointId);
        assertThat(secondReindexPointId).isNotEqualTo(initialPointId);
        assertThat(secondReindexPointId).isNotEqualTo(firstReindexPointId);
        assertThat(existingChunk.getQdrantPointId()).isEqualTo(secondReindexPointId);
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
                .upsertPoint(eq(MaterialIndexingService.qdrantPointIdOf(100L, 0)), any(float[].class), any());

        assertThatThrownBy(() -> indexingService.indexMaterialContent(material, "source text"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
    }

    @Test
    void ensureCollectionFailureStopsBeforeChunkSaveAndUpsert() {
        Material material = material();
        MaterialTextChunk textChunk = new MaterialTextChunk(0, "chunk text", "청크 1");
        when(materialTextChunker.chunk("source text")).thenReturn(List.of(textChunk));
        when(materialEmbeddingService.embedChunks(List.of(textChunk)))
                .thenReturn(List.of(new EmbeddedMaterialTextChunk(textChunk, new float[]{0.1f})));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(100L)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("collection"))
                .when(qdrantClient)
                .ensureCollection();

        assertThatThrownBy(() -> indexingService.indexMaterialContent(material, "source text"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
        verify(materialChunkRepository, never()).save(any(MaterialChunk.class));
        verify(qdrantClient, never()).upsertPoint(any(), any(float[].class), any());
        verify(qdrantClient, never()).deletePoints(any());
    }

    @Test
    void embeddingFailureStopsBeforeCollectionAndQdrant() {
        Material material = material();
        MaterialTextChunk textChunk = new MaterialTextChunk(0, "chunk text", "청크 1");
        when(materialTextChunker.chunk("source text")).thenReturn(List.of(textChunk));
        when(materialEmbeddingService.embedChunks(List.of(textChunk)))
                .thenThrow(new MaterialException(MaterialErrorCode.MATERIAL_EMBEDDING_FAILED));

        assertThatThrownBy(() -> indexingService.indexMaterialContent(material, "source text"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_EMBEDDING_FAILED);
        verify(qdrantClient, never()).ensureCollection();
        verify(qdrantClient, never()).upsertPoint(any(), any(float[].class), any());
        verify(materialChunkRepository, never()).save(any(MaterialChunk.class));
    }

    @Test
    void newMaterialIndexingFailureCleansUpAlreadyUpsertedPoints() {
        Material material = material();
        MaterialTextChunk first = new MaterialTextChunk(0, "first chunk", "chunk 1");
        MaterialTextChunk second = new MaterialTextChunk(1, "second chunk", "chunk 2");
        String firstPointId = MaterialIndexingService.qdrantPointIdOf(100L, 0);
        String secondPointId = MaterialIndexingService.qdrantPointIdOf(100L, 1);
        when(materialTextChunker.chunk("source text")).thenReturn(List.of(first, second));
        when(materialEmbeddingService.embedChunks(List.of(first, second)))
                .thenReturn(List.of(
                        new EmbeddedMaterialTextChunk(first, new float[]{0.1f}),
                        new EmbeddedMaterialTextChunk(second, new float[]{0.2f})
                ));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(100L)).thenReturn(List.of());
        when(materialChunkRepository.save(any(MaterialChunk.class))).thenAnswer(invocation -> {
            MaterialChunk chunk = invocation.getArgument(0);
            ReflectionTestUtils.setField(chunk, "id", 300L + chunk.getChunkIndex());
            return chunk;
        });
        org.mockito.Mockito.doNothing()
                .doThrow(new RuntimeException("qdrant"))
                .when(qdrantClient)
                .upsertPoint(any(), any(float[].class), any());

        assertThatThrownBy(() -> indexingService.indexMaterialContent(material, "source text"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
        verify(qdrantClient).upsertPoint(eq(firstPointId), any(float[].class), any());
        ArgumentCaptor<List<String>> deletedPointIds = ArgumentCaptor.forClass(List.class);
        verify(qdrantClient).deletePoints(deletedPointIds.capture());
        assertThat(deletedPointIds.getValue()).containsExactly(firstPointId);
    }

    @Test
    void existingChunkReindexFailureKeepsOldChunksAndDeletesOnlyStagedPoints() {
        Material material = material();
        MaterialTextChunk first = new MaterialTextChunk(0, "new first", "chunk 1");
        MaterialTextChunk second = new MaterialTextChunk(1, "new second", "chunk 2");
        String firstPointId = MaterialIndexingService.qdrantPointIdOf(100L, 0);
        String secondPointId = MaterialIndexingService.qdrantPointIdOf(100L, 1);
        MaterialChunk existingFirst = chunk(material, 0, "old first", firstPointId, 300L);
        MaterialChunk existingSecond = chunk(material, 1, "old second", secondPointId, 301L);
        when(materialTextChunker.chunk("source text")).thenReturn(List.of(first, second));
        when(materialEmbeddingService.embedChunks(List.of(first, second)))
                .thenReturn(List.of(
                        new EmbeddedMaterialTextChunk(first, new float[]{0.1f}),
                        new EmbeddedMaterialTextChunk(second, new float[]{0.2f})
                ));
        when(materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(100L))
                .thenReturn(List.of(existingFirst, existingSecond));
        org.mockito.Mockito.doNothing()
                .doThrow(new RuntimeException("qdrant"))
                .when(qdrantClient)
                .upsertPoint(any(), any(float[].class), any());

        assertThatThrownBy(() -> indexingService.indexMaterialContent(material, "source text"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
        assertThat(existingFirst.getChunkText()).isEqualTo("old first");
        assertThat(existingSecond.getChunkText()).isEqualTo("old second");
        assertThat(existingFirst.getQdrantPointId()).isEqualTo(firstPointId);
        assertThat(existingSecond.getQdrantPointId()).isEqualTo(secondPointId);
        ArgumentCaptor<List<String>> deletedPointIds = ArgumentCaptor.forClass(List.class);
        verify(qdrantClient).deletePoints(deletedPointIds.capture());
        assertThat(deletedPointIds.getValue())
                .doesNotContain(firstPointId, secondPointId)
                .hasSize(1);
        assertThatCode(() -> UUID.fromString(deletedPointIds.getValue().get(0))).doesNotThrowAnyException();
        verify(materialChunkRepository, never()).save(any(MaterialChunk.class));
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
