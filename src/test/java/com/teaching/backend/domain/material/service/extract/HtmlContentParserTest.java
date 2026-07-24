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
    void usesMainFallbackWhenArticleIsMissing() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                "<html><body><main><h1>Main Title</h1><p>Main content</p></main></body></html>",
                List.of()
        );

        assertThat(result.content()).contains("Main content");
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
    void detectsArticleLikeHtmlSignals() {
        assertThat(parser.looksLikeArticle("<meta property=\"og:type\" content=\"article\">")).isTrue();
        assertThat(parser.looksLikeArticle("<div>plain</div>")).isFalse();
    }
}
