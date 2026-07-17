package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.repository.MaterialChunkRepository;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import com.teaching.backend.global.ai.qdrant.QdrantClient;
import com.teaching.backend.global.ai.qdrant.dto.QdrantSearchHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 채팅 질문에 대해 사용자 소유 자료 중 관련 있는 MaterialChunk를 벡터 검색으로 찾는 서비스
@Service
@Transactional(readOnly = true)
public class MaterialSearchService {

    private final OpenAiClient openAiClient;
    private final QdrantClient qdrantClient;
    private final MaterialChunkRepository materialChunkRepository;
    private final int topK;
    private final double similarityThreshold;

    public MaterialSearchService(
            OpenAiClient openAiClient,
            QdrantClient qdrantClient,
            MaterialChunkRepository materialChunkRepository,
            @Value("${rag.top-k}") int topK,
            @Value("${rag.similarity-threshold}") double similarityThreshold
    ) {
        this.openAiClient = openAiClient;
        this.qdrantClient = qdrantClient;
        this.materialChunkRepository = materialChunkRepository;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<MaterialChunk> searchTopChunks(String query, Long userId) {
        float[] vector = openAiClient.embed(query);
        List<QdrantSearchHit> hits = qdrantClient.search(vector, topK, userId);

        List<Long> chunkIds = hits.stream()
                .filter(hit -> hit.score() >= similarityThreshold)
                .map(hit -> ((Number) hit.payload().get("materialChunkId")).longValue())
                .toList();

        if (chunkIds.isEmpty()) {
            return List.of();
        }

        Map<Long, MaterialChunk> chunksById = materialChunkRepository.findByIdIn(chunkIds).stream()
                .collect(Collectors.toMap(MaterialChunk::getId, Function.identity()));

        // Qdrant가 score 내림차순으로 반환하므로 chunkIds 순서를 그대로 보존해서 재정렬
        return chunkIds.stream()
                .map(chunksById::get)
                .filter(chunk -> chunk != null)
                .toList();
    }
}
