package com.teaching.backend.domain.material.service.extract;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlContentParserTest {

    private final HtmlContentParser parser = new HtmlContentParser();

    @Test
    void extractsOgTitleBeforeArticleH1AndDocumentTitle() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                """
                        <html>
                          <head>
                            <meta property="og:title" content="OG Title">
                            <title>Document Title</title>
                          </head>
                          <body><article><h1>Article Title</h1><p>Body</p></article></body>
                        </html>
                        """,
                List.of()
        );

        assertThat(result.title()).isEqualTo("OG Title");
    }

    @Test
    void extractsArticleContentAndRemovesNoiseBlocks() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                """
                        <html><body>
                          <nav>menu</nav>
                          <article>
                            <h1>Title</h1>
                            <script>hidden()</script>
                            <style>.hidden{}</style>
                            <p>First paragraph</p>
                            <p>Second&nbsp;paragraph</p>
                          </article>
                          <footer>footer</footer>
                        </body></html>
                        """,
                List.of()
        );

        assertThat(result.content()).contains("First paragraph");
        assertThat(result.content()).contains("Second paragraph");
        assertThat(result.content()).doesNotContain("menu", "hidden()", "footer");
    }

    @Test
    void keepsArticleBodyAndRemovesUiTextAroundVelogLikePage() {
        ParsedHtmlContent result = parser.parse(
                "https://velog.io/@user/post",
                """
                        <html>
                          <head>
                            <meta property="og:title" content="Velog Post">
                            <meta name="author" content="velog-writer">
                            <meta property="article:published_time" content="2026-07-25T10:00:00+09:00">
                          </head>
                          <body>
                            <header>site header menu login</header>
                            <main>
                              <article class="velog markdown">
                                <header><h1>Article heading</h1><p>follow</p></header>
                                <p>First real article paragraph.</p>
                                <p>Second real article paragraph.</p>
                              </article>
                              <section class="related">previous post next post related post</section>
                              <section class="comments">write comment 3 comments</section>
                            </main>
                            <footer>footer links</footer>
                          </body>
                        </html>
                        """,
                List.of("velog", "markdown", "content")
        );

        assertThat(result.title()).isEqualTo("Velog Post");
        assertThat(result.author()).isEqualTo("velog-writer");
        assertThat(result.publishedAt()).isEqualTo("2026-07-25T10:00");
        assertThat(result.content()).contains("First real article paragraph.", "Second real article paragraph.");
        assertThat(result.content()).doesNotContain("site header menu", "previous post", "write comment", "footer links");
    }

    @Test
    void removesScriptStyleAndNoscriptText() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                """
                        <html><body>
                          <article>
                            <script>window.secret='hidden';</script>
                            <style>.ad { display:block; }</style>
                            <noscript>enable javascript</noscript>
                            <p>Visible article content</p>
                          </article>
                        </body></html>
                        """,
                List.of()
        );

        assertThat(result.content()).contains("Visible article content");
        assertThat(result.content()).doesNotContain("window.secret", "display:block", "enable javascript");
    }

    @Test
    void usesMainFallbackWhenArticleIsMissing() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                "<html><body><main><h1>Main Title</h1><p>Main content</p></main></body></html>",
                List.of()
        );

        assertThat(result.content()).contains("Main content");
    }

    @Test
    void usesBodyAsFinalFallbackWhenArticleAndMainAreMissing() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                "<html><body><h1>Plain Page</h1><p>Simple body content for fallback extraction</p></body></html>",
                List.of("missing-platform-selector")
        );

        assertThat(result.content()).contains("Simple body content for fallback extraction");
    }

    @Test
    void extractsContentByClassSignalBeforeGenericMain() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                """
                        <html><body>
                          <main>Generic main</main>
                          <div class="entry-content"><p>Post body</p></div>
                        </body></html>
                        """,
                List.of("entry-content")
        );

        assertThat(result.content()).contains("Post body");
        assertThat(result.content()).doesNotContain("Generic main");
    }

    @Test
    void fallsBackToDocumentTitleAndTimeDatetimeMetadata() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                """
                        <html>
                          <head><title>Document Fallback Title</title></head>
                          <body>
                            <article>
                              <time datetime="2026-07-26T12:30:00">today</time>
                              <p>Article content with metadata fallback</p>
                            </article>
                          </body>
                        </html>
                        """,
                List.of()
        );

        assertThat(result.title()).isEqualTo("Document Fallback Title");
        assertThat(result.publishedAt()).isEqualTo("2026-07-26T12:30");
        assertThat(result.content()).contains("Article content with metadata fallback");
    }

    @Test
    void removesKoreanUiOnlyLinesWithoutDroppingArticleText() {
        ParsedHtmlContent result = parser.parse(
                "https://velog.io/@user/post",
                """
                        <html><body>
                          <article>
                            <p>\uD314\uB85C\uC6B0</p>
                            <p>\uC774\uC804 \uD3EC\uC2A4\uD2B8</p>
                            <p>Real Korean article text remains.</p>
                            <p>\uB313\uAE00 \uC791\uC131</p>
                          </article>
                        </body></html>
                        """,
                List.of()
        );

        assertThat(result.content()).contains("Real Korean article text remains.");
        assertThat(result.content()).doesNotContain("\uD314\uB85C\uC6B0", "\uC774\uC804 \uD3EC\uC2A4\uD2B8", "\uB313\uAE00 \uC791\uC131");
    }

    @Test
    void detectsArticleLikeHtmlSignals() {
        assertThat(parser.looksLikeArticle("<meta property=\"og:type\" content=\"article\">")).isTrue();
        assertThat(parser.looksLikeArticle("<div>plain</div>")).isFalse();
    }
}
