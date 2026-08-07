package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.dto.ai.MaterialAiHighlightResult;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.entity.MaterialHighlight;
import com.teaching.backend.domain.material.enums.HighlightType;
import com.teaching.backend.domain.material.enums.MaterialAiHighlightType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialHighlightRepository;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.entity.Tag;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialAiAnalysisPersistenceService {

    private static final String PROMPT_VERSION = "v1";
    private static final int MAX_TAG_NAME_LENGTH = 50;
    private static final int MAX_SHORT_SUMMARY_LENGTH = 255;

    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialHighlightRepository materialHighlightRepository;
    private final TagRepository tagRepository;
    private final MaterialTagRepository materialTagRepository;

    public MaterialAnalysis saveAnalysisResult(
            Material material,
            MaterialAiAnalysisResult result
    ) {
        String safeShortSummary = truncate(result.shortSummary(), MAX_SHORT_SUMMARY_LENGTH);

        // material_id는 유니크 제약이라, 재분석(forceAnalyze)으로 기존 자료를 재사용하는 경우 새로
        // insert하면 제약 위반이 난다. 기존 분석이 있으면 그 자리에서 갱신하고(하이라이트는 새로
        // 만들 것이므로 먼저 지움), 없으면 지금처럼 새로 만든다.
        MaterialAnalysis analysis = materialAnalysisRepository.findByMaterialId(material.getId())
                .map(existing -> {
                    clearHighlights(existing);
                    existing.updateAnalysis(safeShortSummary, result.longAnalysis(), PROMPT_VERSION);
                    return existing;
                })
                .orElseGet(() -> materialAnalysisRepository.save(
                        MaterialAnalysis.create(
                                material,
                                safeShortSummary,
                                result.longAnalysis(),
                                PROMPT_VERSION
                        )
                ));
        saveTags(material, result.tags());
        return analysis;
    }

    private void clearHighlights(MaterialAnalysis analysis) {
        List<MaterialHighlight> existingHighlights = materialHighlightRepository.findAllByMaterialId(
                analysis.getMaterial().getId());
        if (!existingHighlights.isEmpty()) {
            materialHighlightRepository.deleteAll(existingHighlights);
        }
    }

    public void saveHighlights(
            MaterialAnalysis analysis,
            List<MaterialAiHighlightResult> highlights
    ) {
        if (highlights == null || highlights.isEmpty()) {
            return;
        }
        if (analysis == null || analysis.getDetailAnalysis() == null) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
        }

        String longAnalysis = analysis.getDetailAnalysis();

        for (MaterialAiHighlightResult highlight : highlights) {
            if (highlight == null || highlight.text() == null || highlight.text().isBlank()) {
                continue;
            }

            String targetText = highlight.text().trim();

            // 단순 exact match
            int start = longAnalysis.indexOf(targetText);
            int end = start + targetText.length();

            // Exact match 실패 시, 마크다운 기호 제거
            if (start < 0) {
                int[] range = findFlexibleIndex(longAnalysis, targetText);
                if (range != null) {
                    start = range[0];
                    end = range[1];
                    targetText = longAnalysis.substring(start, end); // 실제 마크다운 포함 원문 텍스트로 보정
                }
            }

            // 매칭 실패 시 파이프라인 전체를 터뜨리지 않고 로그 후 스킵
            if (start < 0) {
                log.warn("Highlight match failed for text: {}", targetText);
                continue;
            }

            materialHighlightRepository.save(MaterialHighlight.create(
                    analysis,
                    targetText,
                    toHighlightType(highlight.type()),
                    start,
                    end
            ));
        }
    }

    /**
     * 마크다운 기호나 공백 차이가 있어도 longAnalysis 내의 실제 start, end 인덱스를 찾는다.
     */
    private int[] findFlexibleIndex(String source, String target) {

        String cleanTarget = target.replaceAll("[^a-zA-Z0-9가-힣]", "");
        if (cleanTarget.isBlank()) return null;

        String cleanSource = source.replaceAll("[^a-zA-Z0-9가-힣]", "");
        int cleanStart = cleanSource.indexOf(cleanTarget);
        if (cleanStart < 0) return null;

        int cleanCount = 0;
        int realStart = -1;
        int realEnd = -1;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (String.valueOf(c).matches("[a-zA-Z0-9가-힣]")) {
                if (cleanCount == cleanStart) {
                    realStart = i;
                }
                if (cleanCount == cleanStart + cleanTarget.length() - 1) {
                    realEnd = i + 1;
                    break;
                }
                cleanCount++;
            }
        }

        return (realStart != -1 && realEnd != -1) ? new int[]{realStart, realEnd} : null;
    }

    // 새 분석 결과의 태그 집합과 자료에 이미 붙어있는 태그를 맞춘다: 더 이상 나오지 않는 태그는 지우고
    // (재분석으로 낡은 태그가 안 남게), 새로 나온 태그만 추가한다. 이미 붙어있는 태그는 다시 안 건드림.
    private void saveTags(Material material, List<String> tagNames) {
        Set<String> cleanTagNames = tagNames == null
                ? Set.of()
                : tagNames.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isBlank())
                        .map(name -> truncate(name, MAX_TAG_NAME_LENGTH))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        List<MaterialTag> existingMaterialTags = materialTagRepository.findAllWithTagByMaterialIds(
                List.of(material.getId()));

        List<MaterialTag> staleMaterialTags = existingMaterialTags.stream()
                .filter(materialTag -> !cleanTagNames.contains(materialTag.getTag().getName()))
                .toList();
        if (!staleMaterialTags.isEmpty()) {
            materialTagRepository.deleteAll(staleMaterialTags);
        }

        Set<String> existingTagNames = existingMaterialTags.stream()
                .map(materialTag -> materialTag.getTag().getName())
                .collect(Collectors.toSet());

        for (String name : cleanTagNames) {
            if (existingTagNames.contains(name)) {
                continue;
            }
            materialTagRepository.save(MaterialTag.create(material, getOrCreateTag(name)));
        }
    }

    private Tag getOrCreateTag(String name) {
        return tagRepository.findByName(name)
                .orElseGet(() -> {
                    try {
                        tagRepository.insertIfAbsent(name);
                        return tagRepository.findByName(name)
                                .orElseThrow(() -> new NoSuchElementException("Tag not found: " + name));
                    } catch (Exception e) {
                        return tagRepository.findByName(name)
                                .orElseThrow(() -> new NoSuchElementException("Tag creation failed: " + name));
                    }
                });
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private HighlightType toHighlightType(MaterialAiHighlightType type) {
        if (type == MaterialAiHighlightType.CAUTION) {
            return HighlightType.CAUTION;
        }
        return HighlightType.MAIN;
    }
}