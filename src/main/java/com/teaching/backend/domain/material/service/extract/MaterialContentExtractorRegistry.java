package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MaterialContentExtractorRegistry {

    private final Map<PlatformType, MaterialContentExtractor> extractors;

    public MaterialContentExtractorRegistry(List<MaterialContentExtractor> extractors) {
        this.extractors = new EnumMap<>(PlatformType.class);

        for (PlatformType platformType : PlatformType.values()) {
            List<MaterialContentExtractor> supportedExtractors = extractors.stream()
                    .filter(extractor -> extractor.supports(platformType))
                    .toList();
            if (supportedExtractors.size() > 1) {
                throw new IllegalStateException("Duplicate MaterialContentExtractor for " + platformType);
            }
            if (supportedExtractors.size() == 1) {
                this.extractors.put(platformType, supportedExtractors.get(0));
            }
        }
    }

    public ExtractedMaterialContent extract(
            PlatformType platformType,
            String originalUrl
    ) {
        MaterialContentExtractor extractor = extractors.get(platformType);
        if (extractor == null) {
            throw new MaterialException(MaterialErrorCode.UNSUPPORTED_MATERIAL_PLATFORM);
        }

        return extractor.extract(originalUrl);
    }
}
