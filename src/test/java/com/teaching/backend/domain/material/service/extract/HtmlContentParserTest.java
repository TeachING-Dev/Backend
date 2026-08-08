package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.MaterialImageCandidate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
        assertThat(result.publishedAt()).isEqualTo(LocalDateTime.of(2026, 7, 25, 10, 0));
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
    void doesNotRemoveContentContainerJustBecauseClassContainsHiddenAsSubstring() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                """
                        <html><body>
                          <main>Generic main</main>
                          <div class="posthidden-gems">
                            <p>Article content in a class name that only contains hidden as a substring</p>
                          </div>
                        </body></html>
                        """,
                List.of("posthidden-gems")
        );

        assertThat(result.content()).contains("Article content in a class name");
        assertThat(result.content()).doesNotContain("Generic main");
    }

    @Test
    void extractsNestedDivContentWithoutStoppingAtInnerClosingTag() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                """
                        <html><body>
                          <main>Generic main</main>
                          <div class="entry-content">
                            outer-start
                            <div>inner</div>
                            outer-end
                          </div>
                        </body></html>
                        """,
                List.of("entry-content")
        );

        assertThat(result.content()).contains("outer-start", "inner", "outer-end");
        assertThat(result.content()).doesNotContain("Generic main");
    }

    @Test
    void extractsMultipleNestedLevelsAndSiblingBlocksInOrder() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                """
                        <html><body>
                          <div class="post content">
                            <section>
                              <div>
                                <p>first block</p>
                                <div><p>second nested block</p></div>
                              </div>
                            </section>
                            <p>third sibling block</p>
                          </div>
                        </body></html>
                        """,
                List.of("content")
        );

        assertThat(result.content()).contains("first block", "second nested block", "third sibling block");
        assertThat(result.content()).containsPattern("first block\\s+second nested block\\s+third sibling block");
    }

    @Test
    void handlesClassAttributeOrderAndQuoteStyleWithDomParsing() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                """
                        <html>
                          <head>
                            <meta content='Attribute Order Title' property='og:title'>
                            <meta content='writer' name='author'>
                          </head>
                          <body>
                            <main>Generic main</main>
                            <div data-id="1" class='theme entry-content article'>
                              <p>single quote class content</p>
                            </div>
                          </body>
                        </html>
                        """,
                List.of("entry-content")
        );

        assertThat(result.title()).isEqualTo("Attribute Order Title");
        assertThat(result.author()).isEqualTo("writer");
        assertThat(result.content()).contains("single quote class content");
        assertThat(result.content()).doesNotContain("Generic main");
    }

    @Test
    void parsesLongRepeatedHtmlWithoutRegexBacktrackingPath() {
        String repeated = "<div><span>noise</span></div>".repeat(1_000);
        ParsedHtmlContent result = parser.parse(
                "https://example.com",
                """
                        <html><body>
                          %s
                          <article><p>Article content after long repeated html</p></article>
                        </body></html>
                        """.formatted(repeated),
                List.of()
        );

        assertThat(result.content()).contains("Article content after long repeated html");
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
        assertThat(result.publishedAt()).isEqualTo(LocalDateTime.of(2026, 7, 26, 12, 30));
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

    @Test
    void extractsImageCandidatesFromSelectedContentElement() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                """
                        <html><body>
                          <header><img src="/logo.png" alt="logo"></header>
                          <article>
                            <h2>경제 성장 추이</h2>
                            <p>아래 그래프는 최근 5년간 성장률을 보여준다.</p>
                            <figure>
                              <img src="/images/chart.png" alt="경제 성장률" title="차트">
                              <figcaption>연도별 경제 성장률</figcaption>
                            </figure>
                            <p>이후 문단입니다.</p>
                          </article>
                        </body></html>
                        """,
                List.of()
        );

        assertThat(result.imageCandidates()).hasSize(1);
        MaterialImageCandidate candidate = result.imageCandidates().get(0);
        assertThat(candidate.url()).isEqualTo("https://example.com/images/chart.png");
        assertThat(candidate.alt()).isEqualTo("경제 성장률");
        assertThat(candidate.title()).isEqualTo("차트");
        assertThat(candidate.caption()).isEqualTo("연도별 경제 성장률");
        assertThat(candidate.sectionHeading()).isEqualTo("경제 성장 추이");
        assertThat(candidate.context()).contains("연도별 경제 성장률");
    }

    @Test
    void keepsDirectSiblingImageContext() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                """
                        <html><body>
                          <article>
                            <p>Previous paragraph explains the chart trend in detail.</p>
                            <img src="/images/chart.png" alt="chart image">
                            <p>Next paragraph explains the chart result in detail.</p>
                          </article>
                        </body></html>
                        """,
                List.of()
        );

        MaterialImageCandidate candidate = result.imageCandidates().get(0);
        assertThat(candidate.context())
                .contains("Previous paragraph explains")
                .contains("Next paragraph explains");
    }

    @Test
    void usesParentWrapperSiblingTextAsImageContext() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                """
                        <html><body>
                          <article>
                            <p>Before wrapper paragraph explains the diagram meaning.</p>
                            <div class="image-wrapper">
                              <img src="/images/wrapped-diagram.png" alt="wrapped diagram">
                            </div>
                            <p>After wrapper paragraph explains how to apply it.</p>
                          </article>
                        </body></html>
                        """,
                List.of()
        );

        MaterialImageCandidate candidate = result.imageCandidates().get(0);
        assertThat(candidate.context())
                .contains("Before wrapper paragraph")
                .contains("After wrapper paragraph");
    }

    @Test
    void usesLimitedAncestorSiblingTextAsImageContextWithoutLeavingContentElement() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                """
                        <html><body>
                          <p>Outside content text must not be used for image context.</p>
                          <article>
                            <p>Article paragraph before the nested image wrapper.</p>
                            <div class="outer-wrapper">
                              <div class="inner-wrapper">
                                <img src="/images/nested-diagram.png" alt="nested diagram">
                              </div>
                            </div>
                          </article>
                          <p>Outside footer text must not be used either.</p>
                        </body></html>
                        """,
                List.of()
        );

        MaterialImageCandidate candidate = result.imageCandidates().get(0);
        assertThat(candidate.context()).contains("Article paragraph before");
        assertThat(candidate.context()).doesNotContain("Outside content", "Outside footer");
    }

    @Test
    void limitsImageContextLengthAndSkipsNoiseSiblingText() {
        String longContext = "Meaningful article context ".repeat(30);
        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                """
                        <html><body>
                          <article>
                            <div class="share">share this article</div>
                            <p>%s</p>
                            <div class="image-wrapper">
                              <img src="/images/long-context.png" alt="long context image">
                            </div>
                          </article>
                        </body></html>
                        """.formatted(longContext),
                List.of()
        );

        MaterialImageCandidate candidate = result.imageCandidates().get(0);
        assertThat(candidate.context()).contains("Meaningful article context");
        assertThat(candidate.context()).doesNotContain("share this article");
        assertThat(candidate.context()).hasSizeLessThanOrEqualTo(240);
    }

    @Test
    void extractsLazyAndSrcsetImageCandidatesAndFiltersInvalidDuplicatesAndNonContentImages() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                """
                        <html><body>
                          <article>
                            <p>본문</p>
                            <img data-src="/images/lazy.png" alt="lazy image">
                            <img data-original="/images/original.png" alt="original image">
                            <img data-lazy-src="/images/data-lazy.png" alt="data lazy image">
                            <img srcset="/images/srcset-small.png 480w, /images/srcset-large.png 960w" alt="srcset image">
                            <img src="data:image/png;base64,aaa" alt="inline">
                            <img src="blob:https://example.com/id" alt="blob">
                            <img src="javascript:alert(1)" alt="script">
                            <img src="/images/lazy.png" alt="duplicate">
                            <img src="/images/avatar.png" alt="avatar">
                            <img src="/images/pixel.png" width="1" height="1" alt="tracking pixel">
                          </article>
                        </body></html>
                        """,
                List.of()
        );

        assertThat(result.imageCandidates())
                .extracting(MaterialImageCandidate::url)
                .containsExactly(
                        "https://example.com/images/lazy.png",
                        "https://example.com/images/original.png",
                        "https://example.com/images/data-lazy.png",
                        "https://example.com/images/srcset-small.png"
                );
    }

    @Test
    void normalizesImageUrlsWithWhitespacePercentEncodingNonAsciiAndSchemes() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                """
                        <html><body>
                          <article>
                            <img src="/images/relative.png" alt="relative image">
                            <img src="https://cdn.example.com/absolute.png" alt="absolute image">
                            <img src="/images/already%20encoded.png" alt="encoded image">
                            <img src="/images/chart 2026.png" alt="space image">
                            <img data-src="/images/한글.png" alt="non ascii image">
                            <img src="data:image/png;base64,aaa" alt="inline">
                            <img src="blob:https://example.com/id" alt="blob">
                            <img src="javascript:alert(1)" alt="script">
                            <img src="/images/placeholder.png" alt="placeholder">
                          </article>
                        </body></html>
                        """,
                List.of()
        );

        assertThat(result.imageCandidates())
                .extracting(MaterialImageCandidate::url)
                .contains(
                        "https://example.com/images/relative.png",
                        "https://cdn.example.com/absolute.png",
                        "https://example.com/images/already%20encoded.png"
                );
        assertThat(result.imageCandidates())
                .extracting(MaterialImageCandidate::url)
                .anySatisfy(url -> assertThat(url).contains("chart").contains("2026.png"))
                .anySatisfy(url -> assertThat(url).contains("한글").contains(".png"))
                .doesNotContain(
                        "data:image/png;base64,aaa",
                        "blob:https://example.com/id",
                        "javascript:alert(1)",
                        "https://example.com/images/placeholder.png"
                );
    }

    @Test
    void nonContentImageFilteringUsesTokenMatchingWithoutDroppingSiliconChart() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                """
                        <html><body>
                          <article>
                            <img src="/images/silicon-chart.png" alt="semiconductor silicon chart">
                            <img src="/icons/menu.png" alt="menu icon">
                            <img src="/profile/avatar.png" alt="author avatar">
                            <img src="/images/banner-ad.png" alt="advertisement banner">
                            <img src="/images/tracking-pixel.gif" alt="tracking pixel">
                          </article>
                        </body></html>
                        """,
                List.of()
        );

        assertThat(result.imageCandidates())
                .extracting(MaterialImageCandidate::url)
                .containsExactly("https://example.com/images/silicon-chart.png");
    }

    @Test
    void selectsUsefulImageCandidatesAcrossEntireDocumentWhenMoreThanLimitExist() {
        StringBuilder images = new StringBuilder();
        for (int index = 1; index <= 25; index++) {
            if (index == 25) {
                images.append("""
                        <figure>
                          <img src="/images/important-25.png" alt="architecture diagram">
                          <figcaption>Important architecture diagram for the later section</figcaption>
                        </figure>
                        """);
            } else {
                images.append("<img src=\"/images/plain-%d.png\" alt=\"plain image %d\">".formatted(index, index));
            }
        }

        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                """
                        <html><body>
                          <article>
                            <h2>Architecture section</h2>
                            <p>Article text with enough content for image candidate extraction.</p>
                            %s
                          </article>
                        </body></html>
                        """.formatted(images),
                List.of()
        );

        assertThat(result.imageCandidates()).hasSize(20);
        assertThat(result.imageCandidates())
                .extracting(MaterialImageCandidate::url)
                .contains("https://example.com/images/important-25.png");
    }

    @Test
    void keepsSelectedImageCandidatesInOriginalDomOrder() {
        StringBuilder images = new StringBuilder();
        for (int index = 1; index <= 45; index++) {
            images.append("<img src=\"/images/image-%02d.png\" alt=\"article image %d\">".formatted(index, index));
        }

        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                """
                        <html><body>
                          <article>
                            <p>Article text with many images spread through the document.</p>
                            %s
                          </article>
                        </body></html>
                        """.formatted(images),
                List.of()
        );

        assertThat(result.imageCandidates()).hasSize(20);
        assertThat(result.imageCandidates())
                .extracting(MaterialImageCandidate::url)
                .contains("https://example.com/images/image-45.png");
        assertThat(result.imageCandidates())
                .extracting(MaterialImageCandidate::url)
                .isSorted();
    }

    @Test
    void preservesConfiguredContentSelectorInsideNoisyWrapper() {
        ParsedHtmlContent result = parser.parse(
                "https://blog.naver.com/PostView.naver?blogId=writer&logNo=123",
                """
                        <html><body>
                          <div class="wrap_postcomment">
                            <div class="se-main-container">
                              <h2>Naver Article</h2>
                              <p>Naver article body with enough meaningful text.</p>
                              <img src="https://postfiles.pstatic.net/content.png" alt="content chart">
                              <img src="https://example.com/profile.png" alt="profile">
                            </div>
                          </div>
                        </body></html>
                        """,
                List.of("se-main-container"),
                List.of(".se-main-container")
        );

        assertThat(result.content()).contains("Naver article body");
        assertThat(result.imageCandidates())
                .extracting(MaterialImageCandidate::url)
                .containsExactly("https://postfiles.pstatic.net/content.png");
    }

    @Test
    void returnsEmptyImageCandidatesWhenArticleHasNoImages() {
        ParsedHtmlContent result = parser.parse(
                "https://example.com/posts/1",
                "<html><body><article><p>Only text content</p></article></body></html>",
                List.of()
        );

        assertThat(result.imageCandidates()).isEmpty();
    }
}
