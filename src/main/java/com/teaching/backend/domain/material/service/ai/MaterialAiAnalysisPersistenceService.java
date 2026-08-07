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

    private static final String PROMPT_VERSION = "v2";
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
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
