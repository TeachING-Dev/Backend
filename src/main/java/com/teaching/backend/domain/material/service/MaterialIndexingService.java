package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.dto.indexing.EmbeddedMaterialTextChunk;
import com.teaching.backend.domain.material.dto.indexing.MaterialTextChunk;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialChunkRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.material.service.indexing.MaterialEmbeddingService;
import com.teaching.backend.domain.material.service.indexing.MaterialTextChunker;
import com.teaching.backend.global.ai.qdrant.QdrantClient;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialIndexingService {

    private final MaterialRepository materialRepository;
    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final MaterialTextChunker materialTextChunker;
    private final MaterialEmbeddingService materialEmbeddingService;
    private final QdrantClient qdrantClient;

    @Transactional
    public int indexMaterial(Long materialId, Long userId) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

        if (!material.getUser().getId().equals(userId)) {
            throw new GeneralException(GlobalErrorCode.FORBIDDEN);
        }

        MaterialAnalysis analysis = materialAnalysisRepository.findByMaterialId(materialId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

        return indexMaterialContent(material, analysis.getDetailAnalysis());
    }

    @Transactional
    public int indexMaterialContent(Material material, String text) {
        List<MaterialTextChunk> chunks = materialTextChunker.chunk(text);
        if (chunks.isEmpty()) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_INDEXING_TEXT_EMPTY);
        }

        log.info("Material indexing started. materialId={}, chunkCount={}", material.getId(), chunks.size());
        List<EmbeddedMaterialTextChunk> embeddedChunks = materialEmbeddingService.embedChunks(chunks);
        if (embeddedChunks.size() != chunks.size()) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_EMBEDDING_FAILED);
        }

        List<MaterialChunk> existingChunks = materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(material.getId());
        Map<Integer, MaterialChunk> existingChunkByIndex = existingChunks.stream()
                .collect(Collectors.toMap(MaterialChunk::getChunkIndex, Function.identity(), (current, ignored) -> current));

        ensureCollection();
        try {
            for (EmbeddedMaterialTextChunk embeddedChunk : embeddedChunks) {
                MaterialTextChunk textChunk = embeddedChunk.chunk();
                String pointId = pointIdOf(material.getId(), textChunk.chunkIndex());
                MaterialChunk chunk = existingChunkByIndex.get(textChunk.chunkIndex());
                if (chunk == null) {
                    chunk = materialChunkRepository.save(
                            MaterialChunk.create(material, textChunk.chunkIndex(), textChunk.text(), pointId, textChunk.position())
                    );
                } else {
                    chunk.updateContent(textChunk.text(), pointId, textChunk.position());
                }

                upsertPoint(material, chunk, embeddedChunk.vector());
            }
            deleteExcessChunks(existingChunks, chunks.size());
        } catch (RuntimeException e) {
            log.warn("Material indexing failed. materialId={}, reason={}", material.getId(), e.getClass().getSimpleName());
            throw e;
        }

        log.info("Material indexing completed. materialId={}, chunkCount={}", material.getId(), chunks.size());
        return chunks.size();
    }

    private void ensureCollection() {
        try {
            qdrantClient.ensureCollection();
        } catch (RuntimeException e) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
        }
    }

    private void upsertPoint(Material material, MaterialChunk chunk, float[] vector) {
        try {
            qdrantClient.upsertPoint(chunk.getQdrantPointId(), vector, Map.of(
                    "materialChunkId", chunk.getId(),
                    "materialId", material.getId(),
                    "userId", material.getUser().getId(),
                    "folderId", material.getFolderId(),
                    "chunkIndex", chunk.getChunkIndex(),
                    "text", chunk.getChunkText(),
                    "materialTitle", material.getTitle(),
                    "originalUrl", material.getOriginalUrl()
            ));
        } catch (RuntimeException e) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
        }
    }

    private void deleteExcessChunks(List<MaterialChunk> existingChunks, int newChunkCount) {
        List<MaterialChunk> excessChunks = existingChunks.stream()
                .filter(chunk -> chunk.getChunkIndex() >= newChunkCount)
                .toList();
        if (excessChunks.isEmpty()) {
            return;
        }

        try {
            qdrantClient.deletePoints(excessChunks.stream()
                    .map(MaterialChunk::getQdrantPointId)
                    .toList());
        } catch (RuntimeException e) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
        }
        materialChunkRepository.deleteAll(excessChunks);
    }

    private String pointIdOf(Long materialId, int chunkIndex) {
        return "material-%d-chunk-%d".formatted(materialId, chunkIndex);
    }
}
