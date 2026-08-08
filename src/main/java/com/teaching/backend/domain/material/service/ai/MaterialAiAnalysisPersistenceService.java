package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisResult;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.tag.entity.MaterialTag;
import com.teaching.backend.domain.tag.entity.Tag;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialAiAnalysisPersistenceService {

    private static final String PROMPT_VERSION = "v3";
    private static final int MAX_TAG_NAME_LENGTH = 50;
    private static final int MAX_SHORT_SUMMARY_LENGTH = 255;

    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final TagRepository tagRepository;
    private final MaterialTagRepository materialTagRepository;

    public MaterialAnalysis saveAnalysisResult(
            Material material,
            MaterialAiAnalysisResult result
    ) {
        String safeShortSummary = truncate(result.shortSummary(), MAX_SHORT_SUMMARY_LENGTH);

        // material_id는 유니크 제약이라, 재분석(forceAnalyze)으로 기존 자료를 재사용하는 경우 새로
        // insert하면 제약 위반이 난다. 기존 분석이 있으면 그 자리에서 갱신하고, 없으면 새로 만든다.
        MaterialAnalysis analysis = materialAnalysisRepository.findByMaterialId(material.getId())
                .map(existing -> {
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
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
