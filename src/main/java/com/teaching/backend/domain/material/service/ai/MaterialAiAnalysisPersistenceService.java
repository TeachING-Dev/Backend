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
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class MaterialAiAnalysisPersistenceService {

    private static final String PROMPT_VERSION = "v1";
    private static final int MAX_TAG_NAME_LENGTH = 50;

    private final MaterialAnalysisRepository materialAnalysisRepository;
    private final MaterialHighlightRepository materialHighlightRepository;
    private final TagRepository tagRepository;
    private final MaterialTagRepository materialTagRepository;

    public MaterialAnalysis saveAnalysisResult(
            Material material,
            MaterialAiAnalysisResult result
    ) {
        MaterialAnalysis analysis = materialAnalysisRepository.save(
                MaterialAnalysis.create(
                        material,
                        result.shortSummary(),
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
        if (analysis == null) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
        }
        String longAnalysis = analysis.getDetailAnalysis();
        if (longAnalysis == null || longAnalysis.isBlank()) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
        }

        for (MaterialAiHighlightResult highlight : highlights) {
            String text = highlight == null ? null : highlight.text();
            if (text == null || text.isBlank()) {
                throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
            }
            int start = longAnalysis.indexOf(text);
            if (start < 0) {
                throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
            }
            materialHighlightRepository.save(MaterialHighlight.create(
                    analysis,
                    text,
                    toHighlightType(highlight.type()),
                    start,
                    start + text.length()
            ));
        }
    }

    private void saveTags(Material material, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }

        new LinkedHashSet<>(tagNames.stream()
                .map(name -> name == null ? "" : name.trim())
                .filter(name -> !name.isBlank())
                .filter(name -> name.length() <= MAX_TAG_NAME_LENGTH)
                .toList())
                .forEach(name -> {
                    Tag tag = getOrCreateTag(name);
                    materialTagRepository.save(MaterialTag.create(material, tag));
                });
    }

    private Tag getOrCreateTag(String name) {
        tagRepository.insertIfAbsent(name);
        return tagRepository.findByName(name)
                .orElseThrow(() -> new NoSuchElementException("Tag was not found after insert: " + name));
    }

    private HighlightType toHighlightType(MaterialAiHighlightType type) {
        if (type == MaterialAiHighlightType.CAUTION) {
            return HighlightType.CAUTION;
        }
        return HighlightType.MAIN;
    }
}
