package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialContentExtractorRegistryTest {

    @Test
    void extractsWithMatchingPlatformExtractor() {
        ExtractedMaterialContent expected = content(PlatformType.VELOG);
        MaterialContentExtractorRegistry registry = new MaterialContentExtractorRegistry(List.of(
                extractor(PlatformType.VELOG, expected),
                extractor(PlatformType.BLOG, content(PlatformType.BLOG))
        ));

        ExtractedMaterialContent result = registry.extract(PlatformType.VELOG, "https://velog.io/@example/post");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void extractsNotionWithNotionExtractorWhenResolverReturnsNotion() {
        ExtractedMaterialContent expected = content(PlatformType.NOTION);
        MaterialContentExtractorRegistry registry = new MaterialContentExtractorRegistry(List.of(
                extractor(PlatformType.NOTION, expected),
                extractor(PlatformType.WEB, content(PlatformType.WEB))
        ));

        ExtractedMaterialContent result = registry.extract(
                PlatformType.NOTION,
                "https://shinmini.notion.site/BE-6b1862e1557a4403a65ffe4df97fb3cc"
        );

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void failsWhenPlatformIsUnsupported() {
        MaterialContentExtractorRegistry registry = new MaterialContentExtractorRegistry(List.of());

        assertThatThrownBy(() -> registry.extract(PlatformType.WEB, "https://example.com"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.UNSUPPORTED_MATERIAL_PLATFORM);
    }

    @Test
    void detectsDuplicateExtractorsForSamePlatform() {
        MaterialContentExtractor first = extractor(PlatformType.VELOG, content(PlatformType.VELOG));
        MaterialContentExtractor second = extractor(PlatformType.VELOG, content(PlatformType.VELOG));

        assertThatThrownBy(() -> new MaterialContentExtractorRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }

    private MaterialContentExtractor extractor(
            PlatformType platformType,
            ExtractedMaterialContent content
    ) {
        return new MaterialContentExtractor() {
            @Override
            public boolean supports(PlatformType supportedPlatformType) {
                return supportedPlatformType == platformType;
            }

            @Override
            public ExtractedMaterialContent extract(String originalUrl) {
                return content;
            }
        };
    }

    private ExtractedMaterialContent content(PlatformType platformType) {
        return new ExtractedMaterialContent(
                "https://example.com",
                platformType,
                "Title",
                "Content",
                null,
                null,
                null
        );
    }
}
