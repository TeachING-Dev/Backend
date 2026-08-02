package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.repository.MaterialChunkRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import com.teaching.backend.global.ai.qdrant.QdrantClient;
import com.teaching.backend.global.ai.qdrant.dto.QdrantSearchHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// 채팅 질문에 대해 사용자 소유 자료 중 관련 있는 MaterialChunk를 찾는 서비스.
// 1순위: 질문 문장에 언급된 자료 제목/태그를 RDB에서 직접 조회(searchByMetadata) — 존재 여부를 묻는
// 메타 질문에 강함. 2순위: Qdrant 임베딩 유사도 검색(searchTopChunks) — 본문 내용 질문에 강함.
// 3순위: 자료 제목/태그 자체를 임베딩해 질문과 의미 비교(searchByTitleTagSimilarity) — 1·2순위가
// 문자 그대로/본문 내용 기준으로 못 잡는 동의어·우회 표현 메타 질문에 대응.
@Service
@Transactional(readOnly = true)
public class MaterialSearchService {

    private final OpenAiClient openAiClient;
    private final QdrantClient qdrantClient;
    private final MaterialRepository materialRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final MaterialTagRepository materialTagRepository;
    private final int topK;
    private final double similarityThreshold;
    private final double metadataSimilarityThreshold;

    public MaterialSearchService(
            OpenAiClient openAiClient,
            QdrantClient qdrantClient,
            MaterialRepository materialRepository,
            MaterialChunkRepository materialChunkRepository,
            MaterialTagRepository materialTagRepository,
            @Value("${rag.top-k}") int topK,
            @Value("${rag.similarity-threshold}") double similarityThreshold,
            @Value("${rag.metadata-similarity-threshold}") double metadataSimilarityThreshold
    ) {
        this.openAiClient = openAiClient;
        this.qdrantClient = qdrantClient;
        this.materialRepository = materialRepository;
        this.materialChunkRepository = materialChunkRepository;
        this.materialTagRepository = materialTagRepository;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.metadataSimilarityThreshold = metadataSimilarityThreshold;
    }

    // 질문 문장 안에 이 사용자의 자료 제목/태그명이 언급됐는지 RDB로 먼저 확인한다(벡터 검색 없음).
    public List<MaterialChunk> searchByMetadata(String question, Long userId) {
        List<Long> materialIds = materialRepository.findIdsMentionedInQuestion(userId, question);
        if (materialIds.isEmpty()) {
            return List.of();
        }

        return chunksForMaterials(materialIds.stream().limit(topK).toList());
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
        List<MaterialChunk> chunks = chunkIds.stream()
                .map(chunksById::get)
                .filter(chunk -> chunk != null)
                .toList();

        // Qdrant 벡터는 MySQL의 자료 삭제와 자동으로 동기화되지 않아, 자료가 삭제된 뒤에도 그 자료의
        // 청크가 검색에 걸릴 수 있다(고아 청크). 그대로 두면 프롬프트 구성 시 Material 지연 로딩에서
        // EntityNotFoundException이 터져 챗봇 전체가 500으로 죽으므로 여기서 걸러낸다.
        return filterChunksWithExistingMaterial(chunks);
    }

    // 자료 제목/태그 자체를 배치 임베딩해 질문과 코사인 유사도로 비교한다(캐싱 없음, 매 호출마다 계산).
    // 1·2순위가 모두 실패했을 때만 호출된다는 전제로, 사용자의 전체 자료를 대상으로 한다.
    public List<MaterialChunk> searchByTitleTagSimilarity(String question, Long userId) {
        List<Material> materials = materialRepository.findAllByUser_Id(userId, Sort.unsorted());
        if (materials.isEmpty()) {
            return List.of();
        }

        Map<Long, List<String>> tagNamesByMaterialId = groupTagNamesByMaterialId(materials);
        List<String> embeddingTexts = materials.stream()
                .map(material -> buildEmbeddingText(material, tagNamesByMaterialId.getOrDefault(material.getId(), List.of())))
                .toList();

        float[] questionVector = openAiClient.embed(question);
        List<float[]> materialVectors = openAiClient.embedBatch(embeddingTexts);

        List<Long> matchedMaterialIds = IntStream.range(0, materials.size())
                .mapToObj(i -> Map.entry(materials.get(i).getId(), cosineSimilarity(questionVector, materialVectors.get(i))))
                .filter(entry -> entry.getValue() >= metadataSimilarityThreshold)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();

        return chunksForMaterials(matchedMaterialIds);
    }

    // 매칭된 자료들의 청크를 전부 반환한다. 예전에는 자료당 대표 청크 1개(가장 앞 chunkIndex)만 골라서,
    // 자료는 제목/태그로 맞게 찾아놓고도 실제 답이 담긴 청크가 대표 청크가 아니면 LLM이 그 내용을 아예
    // 못 보고 fallback으로 새는 문제가 있었다. 색인된 청크가 없는 자료는 결과에서 빠진다.
    //
    // 레포지토리 조회는 material_id 오름차순으로 고정돼 있어(DB 정렬), materialIds 자체가 유사도
    // 내림차순으로 정렬돼 있어도(searchByTitleTagSimilarity) 그 순서가 유실된다. materialIds가 넘어온
    // 순서(유사도 순위)를 그대로 보존하면서, 자료 내부에서는 chunkIndex 순서를 유지하도록 재정렬한다.
    private List<MaterialChunk> chunksForMaterials(List<Long> materialIds) {
        if (materialIds.isEmpty()) {
            return List.of();
        }

        List<MaterialChunk> chunks = materialChunkRepository
                .findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(materialIds);

        Map<Long, List<MaterialChunk>> chunksByMaterialId = chunks.stream()
                .collect(Collectors.groupingBy(
                        chunk -> chunk.getMaterial().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<MaterialChunk> orderedChunks = materialIds.stream()
                .flatMap(materialId -> chunksByMaterialId.getOrDefault(materialId, List.of()).stream())
                .toList();

        // materialIds는 이 메서드 호출 시점에는 존재가 확인된 자료들이지만, 자료 삭제 시
        // material_chunks가 함께 정리되지 않는 경우가 있어 방어적으로 한 번 더 걸러낸다.
        return filterChunksWithExistingMaterial(orderedChunks);
    }

    // chunk.getMaterial().getId()는 프록시의 FK 컬럼만 읽는 것이라 DB 조회 없이 안전하다.
    // material.getTitle() 등 다른 필드에 접근할 때만 지연 로딩이 실행되는데, 그 시점에 자료가 이미
    // 삭제되어 있으면 EntityNotFoundException이 터진다. 그 전에 실제 존재하는 자료인지 배치로 확인한다.
    private List<MaterialChunk> filterChunksWithExistingMaterial(List<MaterialChunk> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        Set<Long> referencedMaterialIds = chunks.stream()
                .map(chunk -> chunk.getMaterial().getId())
                .collect(Collectors.toSet());
        Set<Long> existingMaterialIds = materialRepository.findAllById(referencedMaterialIds).stream()
                .map(Material::getId)
                .collect(Collectors.toSet());

        return chunks.stream()
                .filter(chunk -> existingMaterialIds.contains(chunk.getMaterial().getId()))
                .toList();
    }

    private Map<Long, List<String>> groupTagNamesByMaterialId(List<Material> materials) {
        List<Long> materialIds = materials.stream().map(Material::getId).toList();
        return materialTagRepository.findAllWithTagByMaterialIds(materialIds).stream()
                .collect(Collectors.groupingBy(
                        materialTag -> materialTag.getMaterial().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(materialTag -> materialTag.getTag().getName(), Collectors.toList())
                ));
    }

    private String buildEmbeddingText(Material material, List<String> tagNames) {
        StringBuilder text = new StringBuilder(material.getTitle());
        if (material.getAnalysisTitle() != null) {
            text.append(' ').append(material.getAnalysisTitle());
        }
        if (!tagNames.isEmpty()) {
            text.append(' ').append(String.join(" ", tagNames));
        }
        return text.toString();
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
