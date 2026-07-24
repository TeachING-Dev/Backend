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
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialAiAnalysisPersistenceService {

    private static final String PROMPT_VERSION = "v1";
    private static final int MAX_TAG_NAME_LENGTH = 50;

    private final MaterialAnalysisRepository materialAnalysisRepository;
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
                    Tag tag = tagRepository.findByName(name)
                            .orElseGet(() -> tagRepository.save(Tag.create(name)));
                    materialTagRepository.save(MaterialTag.create(material, tag));
                });
    }
}
