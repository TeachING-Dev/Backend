package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.enums.PlatformType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlPlatformClassifierTest {

    private final HtmlPlatformClassifier classifier = new HtmlPlatformClassifier();

    @Test
    void classifiesClearBlogByArticleSignals() {
        assertThat(classifier.classify(
                "https://example.com/posts/1",
                """
                        <html><head>
                          <meta property="og:type" content="article">
                          <meta name="author" content="writer">
                          <meta property="article:published_time" content="2026-07-24T10:00:00+09:00">
                        </head><body><article>body</article></body></html>
                        """
        )).contains(PlatformType.BLOG);
    }

    @Test
    void classifiesClearCafeByBoardSignals() {
        assertThat(classifier.classify(
                "https://community.example.com/board/1",
                """
                        <html><body>
                          <main class="board">
                            <div data-clubid="1" data-articleid="123">community post</div>
                            <section>reply list</section>
                          </main>
                        </body></html>
                        """
        )).contains(PlatformType.CAFE);
    }

    @Test
    void doesNotClassifyGeneralLandingPageAsBlog() {
        assertThat(classifier.classify(
                "https://example.com",
                "<html><body><main><h1>Landing</h1><p>Product introduction</p></main></body></html>"
        )).isEmpty();
    }

    @Test
    void doesNotClassifyWhenSignalsAreInsufficient() {
        assertThat(classifier.classify(
                "https://example.com/page-like",
                "<html><body><article>Only article tag</article></body></html>"
        )).isEmpty();
    }

    @Test
    void knownBlogAndCafeHostsRemainClassified() {
        assertThat(classifier.classify(
                "https://example.tistory.com/entry/1",
                "<html></html>"
        )).contains(PlatformType.BLOG);
        assertThat(classifier.classify(
                "https://cafe.naver.com/example/1",
                "<html></html>"
        )).contains(PlatformType.CAFE);
    }

    @Test
    void doesNotClassifyGeneralArticleMetadataAsCafe() {
        assertThat(classifier.classify(
                "https://www.hanbit.co.kr/channel/view.html?cmscode=CMS7876574876",
                """
                        <html><head>
                          <meta name="author" content="writer">
                          <meta property="article:published_time" content="2026-07-24T10:00:00+09:00">
                        </head><body>
                          <main>
                            <article>General article body with comments and reply section metadata.</article>
                            <section class="comments">comments</section>
                          </main>
                        </body></html>
                        """
        )).isEmpty();
    }
}
