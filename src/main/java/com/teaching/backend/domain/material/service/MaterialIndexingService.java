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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

        List<MaterialChunk> existingChunks = materialChunkRepository.findAllByMaterial_IdOrderByChunkIndexAsc(material.getId());
        Map<Integer, MaterialChunk> existingChunkByIndex = existingChunks.stream()
                .collect(Collectors.toMap(MaterialChunk::getChunkIndex, Function.identity(), (current, ignored) -> current));
        boolean reindexingExistingMaterial = !existingChunks.isEmpty();
        UUID reindexGenerationId = reindexingExistingMaterial
                ? freshReindexGenerationId(material.getId(), chunks, existingChunkByIndex)
                : null;

        ensureCollection();
        List<String> stagedPointIds = new ArrayList<>();
        List<String> oldPointIdsToDeleteAfterCommit = new ArrayList<>();
        List<PendingChunkUpdate> pendingUpdates = new ArrayList<>();
        try {
            for (EmbeddedMaterialTextChunk embeddedChunk : embeddedChunks) {
                MaterialTextChunk textChunk = embeddedChunk.chunk();
                String pointId = qdrantPointIdOf(material.getId(), textChunk.chunkIndex(), reindexGenerationId);
                MaterialChunk chunk = existingChunkByIndex.get(textChunk.chunkIndex());
                if (chunk == null) {
                    chunk = materialChunkRepository.save(
                            MaterialChunk.create(material, textChunk.chunkIndex(), textChunk.text(), pointId, textChunk.position())
                    );
                } else {
                    pendingUpdates.add(new PendingChunkUpdate(chunk, textChunk.text(), pointId, textChunk.position()));
                    if (!chunk.getQdrantPointId().equals(pointId)) {
                        oldPointIdsToDeleteAfterCommit.add(chunk.getQdrantPointId());
                    }
                }

                upsertPoint(
                        material,
                        chunk.getId(),
                        textChunk.chunkIndex(),
                        textChunk.text(),
                        pointId,
                        embeddedChunk.vector()
                );
                stagedPointIds.add(pointId);
            }
            pendingUpdates.forEach(PendingChunkUpdate::apply);
            oldPointIdsToDeleteAfterCommit.addAll(deleteExcessChunks(existingChunks, chunks.size()));
            registerPointCleanup(stagedPointIds, oldPointIdsToDeleteAfterCommit);
        } catch (RuntimeException e) {
            cleanupStagedPoints(stagedPointIds);
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

    private void upsertPoint(
            Material material,
            Long materialChunkId,
            int chunkIndex,
            String chunkText,
            String pointId,
            float[] vector
    ) {
        try {
            qdrantClient.upsertPoint(pointId, vector, Map.of(
                    "materialChunkId", materialChunkId,
                    "materialId", material.getId(),
                    "userId", material.getUser().getId(),
                    "folderId", material.getFolderId(),
                    "chunkIndex", chunkIndex,
                    "text", chunkText,
                    "materialTitle", material.getTitle(),
                    "originalUrl", material.getOriginalUrl()
            ));
        } catch (RuntimeException e) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_VECTOR_STORE_FAILED);
        }
    }

    private List<String> deleteExcessChunks(List<MaterialChunk> existingChunks, int newChunkCount) {
        List<MaterialChunk> excessChunks = existingChunks.stream()
                .filter(chunk -> chunk.getChunkIndex() >= newChunkCount)
                .toList();
        if (excessChunks.isEmpty()) {
            return List.of();
        }

        materialChunkRepository.deleteAll(excessChunks);
        return excessChunks.stream()
                .map(MaterialChunk::getQdrantPointId)
                .toList();
    }

    private void registerPointCleanup(List<String> stagedPointIds, List<String> oldPointIdsToDeleteAfterCommit) {
        List<String> distinctStagedPointIds = distinct(stagedPointIds);
        List<String> distinctOldPointIds = distinct(oldPointIdsToDeleteAfterCommit).stream()
                .filter(pointId -> !distinctStagedPointIds.contains(pointId))
                .toList();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteOldPoints(distinctOldPointIds);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        cleanupStagedPoints(distinctStagedPointIds);
                    }
                }
            });
            return;
        }

        deleteOldPoints(distinctOldPointIds);
    }

    private void cleanupStagedPoints(List<String> pointIds) {
        List<String> distinctPointIds = distinct(pointIds);
        if (distinctPointIds.isEmpty()) {
            return;
        }

        try {
            qdrantClient.deletePoints(distinctPointIds);
        } catch (RuntimeException cleanupFailure) {
            log.warn(
                    "Failed to clean up Qdrant points after indexing failure. pointCount={}, reason={}",
                    distinctPointIds.size(),
                    cleanupFailure.getClass().getSimpleName()
            );
        }
    }

    private void deleteOldPoints(List<String> pointIds) {
        List<String> distinctPointIds = distinct(pointIds);
        if (distinctPointIds.isEmpty()) {
            return;
        }

        try {
            qdrantClient.deletePoints(distinctPointIds);
        } catch (RuntimeException cleanupFailure) {
            log.warn(
                    "Failed to delete old Qdrant points after indexing commit. pointCount={}, reason={}",
                    distinctPointIds.size(),
                    cleanupFailure.getClass().getSimpleName()
            );
        }
    }

    private List<String> distinct(List<String> pointIds) {
        return pointIds.stream()
                .distinct()
                .toList();
    }

    static String qdrantPointIdOf(Long materialId, int chunkIndex) {
        String source = "material:%d:chunk:%d".formatted(materialId, chunkIndex);
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private UUID freshReindexGenerationId(
            Long materialId,
            List<MaterialTextChunk> chunks,
            Map<Integer, MaterialChunk> existingChunkByIndex
    ) {
        UUID generationId;
        do {
            generationId = UUID.randomUUID();
        } while (hasExistingPointCollision(materialId, chunks, existingChunkByIndex, generationId));
        return generationId;
    }

    private boolean hasExistingPointCollision(
            Long materialId,
            List<MaterialTextChunk> chunks,
            Map<Integer, MaterialChunk> existingChunkByIndex,
            UUID generationId
    ) {
        for (MaterialTextChunk chunk : chunks) {
            MaterialChunk existingChunk = existingChunkByIndex.get(chunk.chunkIndex());
            if (existingChunk == null) {
                continue;
            }
            String stagedPointId = qdrantPointIdOf(materialId, chunk.chunkIndex(), generationId);
            if (stagedPointId.equals(existingChunk.getQdrantPointId())) {
                return true;
            }
        }
        return false;
    }

    private static String qdrantPointIdOf(Long materialId, int chunkIndex, UUID generationId) {
        if (generationId == null) {
            return qdrantPointIdOf(materialId, chunkIndex);
        }

        String source = "material:%d:chunk:%d:generation:%s".formatted(materialId, chunkIndex, generationId);
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private record PendingChunkUpdate(
            MaterialChunk chunk,
            String chunkText,
            String pointId,
            String position
    ) {

        private void apply() {
            chunk.updateContent(chunkText, pointId, position);
        }
    }
}
