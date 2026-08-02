package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialChunkRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.entity.Tag;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import com.teaching.backend.global.ai.qdrant.QdrantClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialSearchServiceTest {

    private static final Long USER_ID = 1L;
    private static final int TOP_K = 5;
    private static final double METADATA_SIMILARITY_THRESHOLD = 0.5;

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private QdrantClient qdrantClient;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialChunkRepository materialChunkRepository;

    @Mock
    private MaterialTagRepository materialTagRepository;

    private MaterialSearchService materialSearchService;

    private MaterialSearchService newService() {
        return new MaterialSearchService(
                openAiClient,
                qdrantClient,
                materialRepository,
                materialChunkRepository,
                materialTagRepository,
                TOP_K,
                0.3,
                METADATA_SIMILARITY_THRESHOLD
        );
    }

    @Test
    void searchByMetadataReturnsEmptyWithoutTouchingChunkRepositoryWhenNoMaterialMentioned() {
        materialSearchService = newService();
        when(materialRepository.findIdsMentionedInQuestion(USER_ID, "오늘 날씨 어때?")).thenReturn(List.of());

        List<MaterialChunk> result = materialSearchService.searchByMetadata("오늘 날씨 어때?", USER_ID);

        assertThat(result).isEmpty();
        verify(materialChunkRepository, never()).findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(anyList());
    }

    @Test
    void searchByMetadataReturnsAllChunksOfMentionedMaterial() {
        materialSearchService = newService();
        Material material = material(101L, "Spring Boot REST API 개발 강의");
        MaterialChunk firstChunk = chunk(1L, material, 0, "Spring Boot 소개");
        MaterialChunk secondChunk = chunk(2L, material, 1, "REST API 설계");
        when(materialRepository.findIdsMentionedInQuestion(USER_ID, "내 자료에 백엔드 관련 자료 있어?"))
                .thenReturn(List.of(101L));
        when(materialChunkRepository.findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(List.of(101L)))
                .thenReturn(List.of(firstChunk, secondChunk));

        List<MaterialChunk> result = materialSearchService.searchByMetadata("내 자료에 백엔드 관련 자료 있어?", USER_ID);

        // 대표 청크 1개만 넘기면 실제 답이 다른 청크에 있을 때 놓치므로, 매칭된 자료의 청크를 전부 반환해야 한다.
        assertThat(result).containsExactly(firstChunk, secondChunk);
    }

    @Test
    void searchByMetadataDropsMentionedMaterialsWithoutIndexedChunks() {
        materialSearchService = newService();
        when(materialRepository.findIdsMentionedInQuestion(USER_ID, "iOS 자료 있어?")).thenReturn(List.of(202L));
        when(materialChunkRepository.findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(List.of(202L)))
                .thenReturn(List.of());

        List<MaterialChunk> result = materialSearchService.searchByMetadata("iOS 자료 있어?", USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void searchByMetadataCapsMentionedMaterialIdsAtTopKBeforeFetchingChunks() {
        materialSearchService = newService();
        List<Long> sixMaterialIds = List.of(1L, 2L, 3L, 4L, 5L, 6L);
        List<Long> cappedIds = List.of(1L, 2L, 3L, 4L, 5L);
        when(materialRepository.findIdsMentionedInQuestion(USER_ID, "질문")).thenReturn(sixMaterialIds);
        when(materialChunkRepository.findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(cappedIds))
                .thenReturn(List.of());

        materialSearchService.searchByMetadata("질문", USER_ID);

        verify(materialChunkRepository).findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(cappedIds);
    }

    @Test
    void searchByTitleTagSimilarityReturnsEmptyWithoutEmbeddingWhenUserHasNoMaterials() {
        materialSearchService = newService();
        when(materialRepository.findAllByUser_Id(USER_ID, Sort.unsorted())).thenReturn(List.of());

        List<MaterialChunk> result = materialSearchService.searchByTitleTagSimilarity("서버 쪽 자료 있어?", USER_ID);

        assertThat(result).isEmpty();
        verify(openAiClient, never()).embed(anyString());
    }

    @Test
    void searchByTitleTagSimilarityOnlyKeepsMaterialsAboveThreshold() {
        materialSearchService = newService();
        Material backend = material(101L, "Spring Boot REST API 개발 강의");
        Material ios = material(102L, "SwiftUI 화면 구성 방법");
        Tag backendTag = tag(1L, "백엔드");
        MaterialChunk backendChunk = chunk(11L, backend, 0, "백엔드 소개");
        when(materialRepository.findAllByUser_Id(USER_ID, Sort.unsorted())).thenReturn(List.of(backend, ios));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(101L, 102L)))
                .thenReturn(List.of(MaterialTag.create(backend, backendTag)));
        when(openAiClient.embed("서버 쪽 자료 있어?")).thenReturn(new float[]{1f, 0f});
        when(openAiClient.embedBatch(List.of("Spring Boot REST API 개발 강의 백엔드", "SwiftUI 화면 구성 방법")))
                .thenReturn(List.of(
                        new float[]{1f, 0f}, // backend: 질문과 완전히 동일한 방향 -> 유사도 1.0
                        new float[]{0f, 1f}  // ios: 질문과 직교 -> 유사도 0.0
                ));
        when(materialChunkRepository.findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(List.of(101L)))
                .thenReturn(List.of(backendChunk));

        List<MaterialChunk> result = materialSearchService.searchByTitleTagSimilarity("서버 쪽 자료 있어?", USER_ID);

        assertThat(result).containsExactly(backendChunk);
        verify(materialChunkRepository, never())
                .findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(List.of(101L, 102L));
    }

    @Test
    void searchByTitleTagSimilarityPreservesSimilarityRankOverMaterialIdOrder() {
        materialSearchService = newService();
        // id는 낮지만 유사도가 더 높은 자료와, id는 높지만 유사도가 더 낮은 자료를 섞어서
        // "material_id 오름차순"이 아니라 "유사도 내림차순"으로 청크가 반환되는지 검증한다.
        Material lowIdHighSimilarity = material(101L, "Spring Boot REST API 개발 강의");
        Material highIdLowSimilarity = material(202L, "SwiftUI 화면 구성 방법");
        MaterialChunk lowIdChunk = chunk(11L, lowIdHighSimilarity, 0, "백엔드 소개");
        MaterialChunk highIdChunk = chunk(22L, highIdLowSimilarity, 0, "iOS 소개");
        when(materialRepository.findAllByUser_Id(USER_ID, Sort.unsorted()))
                .thenReturn(List.of(lowIdHighSimilarity, highIdLowSimilarity));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(101L, 202L))).thenReturn(List.of());
        when(openAiClient.embed("서버 쪽 자료 있어?")).thenReturn(new float[]{1f, 0f});
        when(openAiClient.embedBatch(List.of("Spring Boot REST API 개발 강의", "SwiftUI 화면 구성 방법")))
                .thenReturn(List.of(
                        new float[]{0.8f, 0.6f}, // id 101: 유사도는 더 낮지만 id는 더 작음
                        new float[]{1f, 0f}      // id 202: 유사도는 더 높지만 id는 더 큼 (질문과 완전히 동일 방향 -> 1.0)
                ));
        // 실제 레포지토리는 material_id 오름차순으로 반환하므로(101 -> 202), 그 순서 그대로 스텁한다.
        when(materialChunkRepository.findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(List.of(202L, 101L)))
                .thenReturn(List.of(lowIdChunk, highIdChunk));

        List<MaterialChunk> result = materialSearchService.searchByTitleTagSimilarity("서버 쪽 자료 있어?", USER_ID);

        // material_id 순서(101, 202)가 아니라 유사도 순서(202가 더 높음)대로 202의 청크가 먼저 와야 한다.
        assertThat(result).containsExactly(highIdChunk, lowIdChunk);
    }

    @Test
    void searchByTitleTagSimilarityDropsMatchedMaterialsWithoutIndexedChunks() {
        materialSearchService = newService();
        Material material = material(303L, "Redis 캐시를 활용한 성능 개선");
        when(materialRepository.findAllByUser_Id(USER_ID, Sort.unsorted())).thenReturn(List.of(material));
        when(materialTagRepository.findAllWithTagByMaterialIds(List.of(303L))).thenReturn(List.of());
        when(openAiClient.embed("캐싱 자료 있어?")).thenReturn(new float[]{1f, 0f});
        when(openAiClient.embedBatch(List.of("Redis 캐시를 활용한 성능 개선")))
                .thenReturn(List.of(new float[]{1f, 0f}));
        when(materialChunkRepository.findAllByMaterial_IdInOrderByMaterial_IdAscChunkIndexAsc(List.of(303L)))
                .thenReturn(List.of());

        List<MaterialChunk> result = materialSearchService.searchByTitleTagSimilarity("캐싱 자료 있어?", USER_ID);

        assertThat(result).isEmpty();
    }

    private Material material(Long materialId, String title) {
        User user = User.create("user" + USER_ID + "@example.com", "user" + USER_ID, null, null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Material material = Material.create(user, null, title, "https://example.com", PlatformType.WEB);
        ReflectionTestUtils.setField(material, "id", materialId);
        return material;
    }

    private MaterialChunk chunk(Long chunkId, Material material, int chunkIndex, String text) {
        MaterialChunk chunk = MaterialChunk.create(material, chunkIndex, text, "point-" + chunkId, null, null);
        ReflectionTestUtils.setField(chunk, "id", chunkId);
        return chunk;
    }

    private Tag tag(Long tagId, String name) {
        Tag tag = Tag.create(name);
        ReflectionTestUtils.setField(tag, "id", tagId);
        return tag;
    }
}
