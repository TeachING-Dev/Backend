package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.enums.PlatformType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialPlatformResolverTest {

    private final MaterialPlatformResolver resolver = new MaterialPlatformResolver();

    @Test
    void returnsRequestedPlatformTypeFirst() {
        assertThat(resolver.resolve(PlatformType.BLOG, "https://youtube.com/watch?v=abc"))
                .isEqualTo(PlatformType.BLOG);
    }

    @Test
    void resolvesYoutubeUrl() {
        assertThat(resolver.resolve(null, "https://www.youtube.com/watch?v=abc"))
                .isEqualTo(PlatformType.YOUTUBE);
        assertThat(resolver.resolve(null, "https://youtu.be/abc"))
                .isEqualTo(PlatformType.YOUTUBE);
    }

    @Test
    void resolvesNotionUrl() {
        assertThat(resolver.resolve(null, "https://workspace.notion.so/page"))
                .isEqualTo(PlatformType.NOTION);
    }

    @Test
    void resolvesPdfUrl() {
        assertThat(resolver.resolve(null, "https://example.com/guide.pdf"))
                .isEqualTo(PlatformType.PDF);
    }

    @Test
    void resolvesGeneralUrlAsWeb() {
        assertThat(resolver.resolve(null, "https://example.com/article"))
                .isEqualTo(PlatformType.WEB);
    }
}
