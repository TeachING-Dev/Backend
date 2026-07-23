package com.teaching.backend.domain.teachingmap.service;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TeachingMapPromptGenerator {

    private static final String SYSTEM_PROMPT = """
            당신은 사용자의 수집된 자료를 분석하여 학습 효율을 극대화하는 '티칭맵 설계사'입니다.
            사용자가 선택한 폴더의 자료들을 분석하여 난이도 순으로 정렬된 수직형 로드맵을 생성하세요.

            [모드별 정책]
            Short-cut: 폴더 내 가장 핵심적인 자료 3~5개를 선정하여 구성.
            Deep-dive: 폴더 내 모든 자료를 사용하여 구성.

            [JSON Structure]
            {
              "mode": "선택된 모드 (Short-cut 또는 Deep-dive)",
              "nodes": [
                { "step": 1, "materialId": 12, "title": "수집 자료 원본의 제목", "ai_guide": "한 줄 가이드" }
              ]
            }

            Constraints:
            - 정렬 기준: 자료의 난이도를 분석하여 학습하기 쉬운 순서(기초->심화)대로 step을 부여하십시오.
            - AI 가이드 작성: 유저가 자료를 읽을 때 바로 적용 가능한 실용적인 팁을 작성하십시오.
            - 엄격한 출력: 서론이나 부연 설명 없이 오직 JSON만 출력하십시오.
            """;

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildUserMessage(TeachingMapType type, List<Material> materials,
                                   Map<Long, MaterialAnalysis> analysisByMaterialId) {
        String materialList = materials.stream()
                .map(m -> {
                    MaterialAnalysis analysis = analysisByMaterialId.get(m.getId());
                    String summary = analysis != null ? analysis.getSummary() : "";
                    return "- id: %d, 제목: %s, 난이도: %s, 요약: %s"
                            .formatted(m.getId(), m.getTitle(), m.getDifficulty(), summary);
                })
                .collect(Collectors.joining("\n"));

        return """
                모드: %s

                폴더 내 자료 리스트:
                %s
                """.formatted(type, materialList);
    }
}