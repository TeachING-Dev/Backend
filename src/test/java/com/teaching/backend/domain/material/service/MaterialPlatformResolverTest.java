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
        assertThat(resolver.resolve(null, "https://youtube.com/watch?v=1"))
                .isEqualTo(PlatformType.YOUTUBE);
        assertThat(resolver.resolve(null, "https://www.youtube.com/watch?v=1"))
                .isEqualTo(PlatformType.YOUTUBE);
        assertThat(resolver.resolve(null, "https://m.youtube.com/watch?v=1"))
                .isEqualTo(PlatformType.YOUTUBE);
        assertThat(resolver.resolve(null, "https://youtu.be/abc"))
                .isEqualTo(PlatformType.YOUTUBE);
    }

    @Test
    void resolvesNotionUrl() {
        assertThat(resolver.resolve(null, "https://www.notion.so/page"))
                .isEqualTo(PlatformType.NOTION);
    }

    @Test
    void resolvesPdfUrl() {
        assertThat(resolver.resolve(null, "https://example.com/guide.pdf"))
                .isEqualTo(PlatformType.PDF);
        assertThat(resolver.resolve(null, "https://example.com/document.PDF?download=1"))
                .isEqualTo(PlatformType.PDF);
        assertThat(resolver.resolve(null, "https://example.com/document.pdf#page=2"))
                .isEqualTo(PlatformType.PDF);
    }

    @Test
    void resolvesGeneralUrlAsWeb() {
        assertThat(resolver.resolve(null, "https://example.com/article"))
                .isEqualTo(PlatformType.WEB);
    }

    @Test
    void doesNotClassifyQueryStringAsYoutube() {
        assertThat(resolver.resolve(null, "https://example.com/?next=youtube.com"))
                .isEqualTo(PlatformType.WEB);
    }

    @Test
    void doesNotClassifySimilarDomainsAsSupportedPlatforms() {
        assertThat(resolver.resolve(null, "https://youtube.com.evil.com/page"))
                .isEqualTo(PlatformType.WEB);
        assertThat(resolver.resolve(null, "https://notion.so.evil.com/page"))
                .isEqualTo(PlatformType.WEB);
    }

    @Test
    void doesNotClassifyNonPdfPathAsPdf() {
        assertThat(resolver.resolve(null, "https://example.com/file.pdf.html"))
                .isEqualTo(PlatformType.WEB);
        assertThat(resolver.resolve(null, "https://example.com/?file=document.pdf"))
                .isEqualTo(PlatformType.WEB);
    }
}
