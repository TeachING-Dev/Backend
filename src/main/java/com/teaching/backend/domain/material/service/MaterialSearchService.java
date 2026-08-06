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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// 채팅 질문에 대해 사용자 소유 자료 중 관련 있는 MaterialChunk를 찾는 서비스.
// 1순위: 질문 문장에 언급된 자료 제목/태그를 RDB에서 직접 조회(searchByMetadata) — 존재 여부를 묻는
// 메타 질문에 강함. 2순위: 질문에서 뽑은 키워드가 청크 본문에 그대로 있는지 조회(searchByContentKeyword)
// — 본문에 단어가 실제로 있는데도 임베딩 유사도가 임계값을 못 넘어 놓치는 경우를 막는다. 3순위: 원본
// 질문 + LLM이 만든 여러 표현으로 각각 Qdrant 임베딩 검색해 합치는 멀티쿼리 검색(searchByMultiQueryVector)
// — 단어가 그대로 없는 의미/맥락 질문이나, 질문 표현이 자료 본문과 안 맞아 단일 쿼리로는 유사도가
// 낮게 나오는 경우의 recall을 높인다. 4순위: 자료 제목/태그 자체를 임베딩해 질문과 의미 비교
// (searchByTitleTagSimilarity) — 앞 순위가 전부 못 잡는 동의어·우회 표현 메타 질문에 대응.
@Service
@Transactional(readOnly = true)
public class MaterialSearchService {

    // 조사 오탐을 줄이려고 긴 조사부터 먼저 비교(예: "에서"를 "서"보다 먼저 떼어내야 함).
    private static final List<String> TRAILING_PARTICLES = List.of(
            "에서", "에게", "한테", "부터", "까지", "이나",
            "은", "는", "이", "가", "을", "를", "과", "와", "도", "의", "나", "만"
    );
    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_KEYWORDS = 5;

    private static final int MULTI_QUERY_VARIANT_COUNT = 3;
    private static final String MULTI_QUERY_EXPANSION_PROMPT = """
            사용자의 질문과 같은 의도를 유지하면서, 검색에 도움이 되도록 다른 단어와 문장 구조를 사용한
            표현 %d개를 만들어 주세요. 각 표현은 한 줄에 하나씩, 번호나 설명 없이 질문 문장만 출력하세요.
            """.formatted(MULTI_QUERY_VARIANT_COUNT);

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

    // 질문에서 뽑은 키워드가 청크 본문에 문자 그대로 있는지 확인한다. 임베딩 유사도는 "의미"가 가까운지를
    // 보는 것이라, 본문에 단어가 정확히 있어도 문장 전체 벡터 유사도가 임계값을 못 넘으면 놓칠 수 있다.
    // 그 사각지대를 메우기 위한 직접 매칭 단계.
    public List<MaterialChunk> searchByContentKeyword(String question, Long userId) {
        List<String> keywords = extractKeywords(question);
        if (keywords.isEmpty()) {
            return List.of();
        }

        Map<Long, MaterialChunk> matchedChunks = new LinkedHashMap<>();
        for (String keyword : keywords) {
            int remaining = topK - matchedChunks.size();
            if (remaining <= 0) {
                break;
            }
            List<MaterialChunk> hits = materialChunkRepository.findByUserIdAndChunkTextContaining(
                    userId, keyword, PageRequest.of(0, remaining));
            for (MaterialChunk chunk : hits) {
                matchedChunks.putIfAbsent(chunk.getId(), chunk);
            }
        }

        return matchedChunks.values().stream().limit(topK).toList();
    }

    // 공백/문장부호로 어절을 나누고, 너무 짧은 토큰은 버린 뒤, 흔한 조사를 떼어낸다. 형태소 분석기 없이
    // 하는 휴리스틱이라 완벽하진 않지만("먹었어요"처럼 어미가 붙은 동사는 못 잡음), 명사형 키워드
    // 질문에는 충분히 잘 맞는다. 긴 토큰일수록 더 구체적인 키워드일 가능성이 높아 우선 검색한다.
    private List<String> extractKeywords(String question) {
        return Arrays.stream(question.split("[\\s,.?!()\\[\\]{}:;\"'~`]+"))
                .map(this::stripTrailingParticle)
                .filter(token -> token.length() >= MIN_KEYWORD_LENGTH)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(MAX_KEYWORDS)
                .toList();
    }

    private String stripTrailingParticle(String token) {
        for (String particle : TRAILING_PARTICLES) {
            if (token.length() > particle.length() && token.endsWith(particle)) {
                return token.substring(0, token.length() - particle.length());
            }
        }
        return token;
    }

    public List<MaterialChunk> searchTopChunks(String query, Long userId) {
        return searchByBestScoringChunks(List.of(query), userId);
    }

    // 원본 질문 하나만으로는 자료 본문과 표현이 달라(동의어/우회 표현) 임베딩 유사도가 낮게 나와 놓치는
    // 경우가 있다. LLM으로 같은 의도의 다른 표현 질문 여러 개를 만들어 각각 벡터 검색한 뒤 합쳐서
    // recall을 높인다(Multi-Query Retriever). 같은 청크가 여러 질문에 걸리면 그중 가장 높은 유사도를 쓴다.
    public List<MaterialChunk> searchByMultiQueryVector(String question, Long userId) {
        return searchByBestScoringChunks(buildQueryVariants(question), userId);
    }

    // 질문마다 topK개씩 벡터 검색해 임계값 이상인 것만 모으고, 같은 청크가 여러 질문에서 잡히면
    // 그중 최고 점수를 채택해 병합한 뒤 상위 topK만 남긴다. 단일 질문이면 병합할 게 없으니 Qdrant가
    // 반환한 순서(score 내림차순) 그대로다.
    private List<MaterialChunk> searchByBestScoringChunks(List<String> queries, Long userId) {
        Map<Long, Double> bestScoreByChunkId = new LinkedHashMap<>();
        for (String query : queries) {
            float[] vector = openAiClient.embed(query);
            for (QdrantSearchHit hit : qdrantClient.search(vector, topK, userId)) {
                if (hit.score() < similarityThreshold) {
                    continue;
                }
                long chunkId = ((Number) hit.payload().get("materialChunkId")).longValue();
                bestScoreByChunkId.merge(chunkId, hit.score(), Math::max);
            }
        }

        if (bestScoreByChunkId.isEmpty()) {
            return List.of();
        }

        List<Long> chunkIds = bestScoreByChunkId.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();

        Map<Long, MaterialChunk> chunksById = materialChunkRepository.findByIdIn(chunkIds).stream()
                .collect(Collectors.toMap(MaterialChunk::getId, Function.identity()));

        List<MaterialChunk> chunks = chunkIds.stream()
                .map(chunksById::get)
                .filter(chunk -> chunk != null)
                .toList();

        // Qdrant 벡터는 MySQL의 자료 삭제와 자동으로 동기화되지 않아, 자료가 삭제된 뒤에도 그 자료의
        // 청크가 검색에 걸릴 수 있다(고아 청크). 그대로 두면 프롬프트 구성 시 Material 지연 로딩에서
        // EntityNotFoundException이 터져 챗봇 전체가 500으로 죽으므로 여기서 걸러낸다.
        return filterChunksWithExistingMaterial(chunks);
    }

    // LLM에게 같은 의도의 다른 표현을 요청해 원본 질문에 덧붙인다. 이 호출 자체가 실패하면(타임아웃 등)
    // 다른 OpenAI 호출들과 동일하게 예외가 그대로 전파되어 ask()가 quota를 반환하고 실패 처리한다 —
    // 검색 품질을 위해 조용히 원본 질문만으로 저하시키지 않는다.
    private List<String> buildQueryVariants(String question) {
        List<String> variants = new ArrayList<>();
        variants.add(question);

        String response = openAiClient.chatComplete(MULTI_QUERY_EXPANSION_PROMPT, question);
        response.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .limit(MULTI_QUERY_VARIANT_COUNT)
                .forEach(variants::add);

        return variants;
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
