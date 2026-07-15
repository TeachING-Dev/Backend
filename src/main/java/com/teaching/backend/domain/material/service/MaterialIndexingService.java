package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialChunkRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import com.teaching.backend.global.ai.qdrant.QdrantClient;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Material의 분석 결과(detailAnalysis)를 청킹 -> 임베딩 -> Qdrant 색인하는 서비스.
// TODO: 자료 분석 완료 이벤트/파이프라인이 생기면 자동 트리거로 대체 (현재는 MaterialController의 수동 엔드포인트로만 호출됨)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialIndexingService {

    private final MaterialRepository materialRepository;
    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final OpenAiClient openAiClient;
    private final QdrantClient qdrantClient;

    // 루프 안에서 블로킹 HTTP 호출(임베딩/Qdrant upsert)을 반복하는 동안 트랜잭션이 열려있음.
    // 수동 트리거용 저볼륨 엔드포인트라 지금은 허용하되, 실제 비동기 색인 파이프라인으로 대체될 때 재검토 필요.
    @Transactional
    public int indexMaterial(Long materialId, Long userId) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

        if (!material.getUser().getId().equals(userId)) {
            throw new GeneralException(GlobalErrorCode.FORBIDDEN);
        }

        // 이미 색인된 자료 재색인 시 중복 벡터/청크 생성 방지 (재색인/삭제 로직은 범위 밖 TODO)
        if (materialChunkRepository.existsByMaterialId(materialId)) {
            throw new GeneralException(GlobalErrorCode.CONFLICT);
        }

        MaterialAnalysis analysis = materialAnalysisRepository.findByMaterialId(materialId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

        List<String> chunkTexts = TextChunker.chunk(analysis.getDetailAnalysis());
        if (chunkTexts.isEmpty()) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        qdrantClient.ensureCollection();

        for (int i = 0; i < chunkTexts.size(); i++) {
            String text = chunkTexts.get(i);
            float[] vector = openAiClient.embed(text);
            String pointId = UUID.randomUUID().toString();

            MaterialChunk chunk = materialChunkRepository.save(
                    MaterialChunk.create(material, i, text, pointId, null)
            );

            qdrantClient.upsertPoint(pointId, vector, Map.of(
                    "materialChunkId", chunk.getId(),
                    "materialId", material.getId(),
                    "userId", material.getUser().getId()
            ));
        }

        return chunkTexts.size();
    }
}
