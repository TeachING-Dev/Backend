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

        MaterialAnalysis analysis = materialAnalysisRepository.save(
                MaterialAnalysis.create(
                        material,
                        safeShortSummary,
                        result.longAnalysis(),
                        PROMPT_VERSION
                )
        );
        saveTags(material, result.tags());
        return analysis;
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

    private void saveTags(Material material, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }

        Set<String> cleanTagNames = tagNames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .map(name -> truncate(name, MAX_TAG_NAME_LENGTH))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String name : cleanTagNames) {
            Tag tag = getOrCreateTag(name);

            boolean exists = materialTagRepository.existsByMaterial_IdAndTag_Id(material.getId(), tag.getId());
            if (!exists) {
                materialTagRepository.save(MaterialTag.create(material, tag));
            }
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