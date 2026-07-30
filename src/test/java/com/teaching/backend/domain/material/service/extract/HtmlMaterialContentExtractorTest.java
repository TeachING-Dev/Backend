package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HtmlMaterialContentExtractorTest {

    private static final String URL = "https://example.com/post";

    @Test
    void velogExtractorExtractsOgTitleAndArticleBody() {
        ExternalHtmlDocumentClient client = client("""
                <html>
                  <head><meta property="og:title" content="Velog Title"></head>
                  <body><article><p>Velog body with enough article content for extraction</p></article></body>
                </html>
                """);
        VelogMaterialContentExtractor extractor = new VelogMaterialContentExtractor(client);

        ExtractedMaterialContent result = extractor.extract(URL);

        assertThat(result.platformType()).isEqualTo(PlatformType.VELOG);
        assertThat(result.title()).isEqualTo("Velog Title");
        assertThat(result.content()).contains("Velog body");
    }

    @Test
    void velogExtractorDoesNotUseRenderedFallbackWhenStaticExtractionSucceeds() {
        ExternalHtmlDocumentClient client = client("""
                <html>
                  <body><article><p>Static Velog body with enough content for extraction</p></article></body>
                </html>
                """);
        RenderedHtmlDocumentClient renderedClient = mock(RenderedHtmlDocumentClient.class);
        VelogMaterialContentExtractor extractor = new VelogMaterialContentExtractor(client, renderedClient);

        ExtractedMaterialContent result = extractor.extract(URL);

        assertThat(result.content()).contains("Static Velog body");
        verify(renderedClient, never()).render(URL);
    }

    @Test
    void velogExtractorExcludesPageUiAroundArticleContent() {
        ExternalHtmlDocumentClient client = client("""
                <html>
                  <head><meta property="og:title" content="Velog Title"></head>
                  <body>
                    <header>login navigation</header>
                    <main>
                      <article class="markdown-body">
                        <p>Clean Velog article content for downstream analysis.</p>
                      </article>
                      <section class="related">previous post next post</section>
                      <section class="comments">write comment</section>
                    </main>
                    <footer>footer links</footer>
                  </body>
                </html>
                """);
        VelogMaterialContentExtractor extractor = new VelogMaterialContentExtractor(client);

        ExtractedMaterialContent result = extractor.extract(URL);

        assertThat(result.content()).contains("Clean Velog article content");
        assertThat(result.content()).doesNotContain("login navigation", "previous post", "write comment", "footer links");
    }

    @Test
    void blogExtractorUsesArticleAndExcludesNoise() {
        ExternalHtmlDocumentClient client = client("""
                <html><body>
                  <nav>menu</nav>
                  <article><h1>Blog</h1><p>Article body with enough public blog content</p></article>
                  <footer>comment</footer>
                </body></html>
                """);
        BlogMaterialContentExtractor extractor = new BlogMaterialContentExtractor(client);

        ExtractedMaterialContent result = extractor.extract(URL);

        assertThat(result.platformType()).isEqualTo(PlatformType.BLOG);
        assertThat(result.content()).contains("Article body");
        assertThat(result.content()).doesNotContain("menu", "comment");
    }

    @Test
    void dedicatedBlogExtractorKeepsBlogPlatformTypeEvenWhenHtmlContainsCafeSignals() {
        ExternalHtmlDocumentClient client = client("""
                <html><body>
                  <article class="board" data-clubid="1" data-articleid="2">
                    Dedicated blog extractor content with enough text and reply signals
                  </article>
                </body></html>
                """);
        BlogMaterialContentExtractor extractor = new BlogMaterialContentExtractor(client);

        ExtractedMaterialContent result = extractor.extract(URL);

        assertThat(result.platformType()).isEqualTo(PlatformType.BLOG);
        assertThat(result.content()).contains("Dedicated blog extractor content");
    }

    @Test
    void genericWebExtractorClassifiesPublicBlogAndExtractsContent() {
        ExternalHtmlDocumentClient client = client("""
                <html>
                  <head>
                    <meta property="og:type" content="article">
                    <meta name="author" content="writer">
                    <meta property="article:published_time" content="2026-07-24T10:00:00+09:00">
                  </head>
                  <body><article><p>Generic public blog article content for analysis</p></article></body>
                </html>
                """);
        GenericWebMaterialContentExtractor extractor = new GenericWebMaterialContentExtractor(
                client,
                new HtmlPlatformClassifier()
        );

        ExtractedMaterialContent result = extractor.extract(URL);

        assertThat(result.platformType()).isEqualTo(PlatformType.BLOG);
        assertThat(result.content()).contains("Generic public blog article content");
    }

    @Test
    void genericWebExtractorUsesWebFallbackForUnclassifiedPage() {
        ExternalHtmlDocumentClient client = client("""
                <html><body><main><h1>Product</h1><p>Landing page copy with no article signals.</p></main></body></html>
                """);
        GenericWebMaterialContentExtractor extractor = new GenericWebMaterialContentExtractor(
                client,
                new HtmlPlatformClassifier()
        );

        ExtractedMaterialContent result = extractor.extract(URL);

        assertThat(result.platformType()).isEqualTo(PlatformType.WEB);
        assertThat(result.content()).contains("Landing page copy");
    }

    @Test
    void genericWebExtractorUsesRenderedFallbackWhenStaticExtractionIsEmpty() {
        ExternalHtmlDocumentClient client = client("<html><body><main>short</main></body></html>");
        RenderedHtmlDocumentClient renderedClient = mock(RenderedHtmlDocumentClient.class);
        when(renderedClient.render(URL)).thenReturn(Optional.of(new HtmlDocument(
                URL,
                "<html><body><article>Rendered generic web content with enough text</article></body></html>",
                "text/html"
        )));
        GenericWebMaterialContentExtractor extractor = new GenericWebMaterialContentExtractor(
                client,
                new HtmlPlatformClassifier(),
                renderedClient
        );

        ExtractedMaterialContent result = extractor.extract(URL);

        assertThat(result.platformType()).isEqualTo(PlatformType.WEB);
        assertThat(result.content()).contains("Rendered generic web content");
        verify(renderedClient).render(URL);
    }

    @Test
    void blogExtractorExtractsTistorySkinContentByAdditionalSelector() {
        ExternalHtmlDocumentClient client = client("""
                <html><body>
                  <header>site header</header>
                  <div class="area_view"><p>Tistory skin article content with enough text</p></div>
                </body></html>
                """);
        RenderedHtmlDocumentClient renderedClient = mock(RenderedHtmlDocumentClient.class);
        BlogMaterialContentExtractor extractor = new BlogMaterialContentExtractor(client, renderedClient);

        ExtractedMaterialContent result = extractor.extract(URL);

        assertThat(result.content()).contains("Tistory skin article content");
        verify(renderedClient, never()).render(URL);
    }

    @Test
    void blogExtractorFollowsNaverBlogMainFrameBeforeRenderedFallback() {
        String outerUrl = "https://blog.naver.com/writer/123";
        String frameUrl = "https://blog.naver.com/PostView.naver?blogId=writer&logNo=123";
        ExternalHtmlDocumentClient client = mock(ExternalHtmlDocumentClient.class);
        when(client.fetch(outerUrl)).thenReturn(new HtmlDocument(
                outerUrl,
                "<html><body><iframe id=\"mainFrame\" src=\"/PostView.naver?blogId=writer&logNo=123\"></iframe></body></html>",
                "text/html"
        ));
        when(client.fetch(frameUrl)).thenReturn(new HtmlDocument(
                frameUrl,
                "<html><body><div class=\"se-main-container\">Naver blog iframe content with enough body text</div></body></html>",
                "text/html"
        ));
        RenderedHtmlDocumentClient renderedClient = mock(RenderedHtmlDocumentClient.class);
        BlogMaterialContentExtractor extractor = new BlogMaterialContentExtractor(client, renderedClient);

        ExtractedMaterialContent result = extractor.extract(outerUrl);

        assertThat(result.content()).contains("Naver blog iframe content");
        verify(renderedClient, never()).render(outerUrl);
    }

    @Test
    void blogExtractorUsesRenderedFallbackWhenStaticAndIframeExtractionFail() {
        String blogUrl = "https://blog.naver.com/writer/123";
        ExternalHtmlDocumentClient client = mock(ExternalHtmlDocumentClient.class);
        when(client.fetch(blogUrl)).thenReturn(new HtmlDocument(
                blogUrl,
                "<html><body><iframe id=\"mainFrame\" src=\"https://evil.example.com/post\"></iframe></body></html>",
                "text/html"
        ));
        RenderedHtmlDocumentClient renderedClient = mock(RenderedHtmlDocumentClient.class);
        when(renderedClient.render(blogUrl)).thenReturn(Optional.of(new HtmlDocument(
                blogUrl,
                "<html><body><article>Rendered naver blog content with enough text</article></body></html>",
                "text/html"
        )));
        BlogMaterialContentExtractor extractor = new BlogMaterialContentExtractor(client, renderedClient);

        ExtractedMaterialContent result = extractor.extract(blogUrl);

        assertThat(result.content()).contains("Rendered naver blog content");
        verify(renderedClient).render(blogUrl);
    }

    @Test
    void cafeExtractorRejectsLoginRequiredPage() {
        ExternalHtmlDocumentClient client = client("<html><body>로그인 후 카페 회원 권한이 필요합니다.</body></html>");
        CafeMaterialContentExtractor extractor = new CafeMaterialContentExtractor(client);

        assertThatThrownBy(() -> extractor.extract(URL))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
    }

    @Test
    void cafeExtractorFollowsNaverCafeFrameBeforeRenderedFallback() {
        String outerUrl = "https://cafe.naver.com/community/123";
        String frameUrl = "https://cafe.naver.com/ArticleRead.nhn?clubid=1&articleid=123";
        ExternalHtmlDocumentClient client = mock(ExternalHtmlDocumentClient.class);
        when(client.fetch(outerUrl)).thenReturn(new HtmlDocument(
                outerUrl,
                "<html><body><iframe id=\"cafe_main\" src=\"/ArticleRead.nhn?clubid=1&articleid=123\"></iframe></body></html>",
                "text/html"
        ));
        when(client.fetch(frameUrl)).thenReturn(new HtmlDocument(
                frameUrl,
                "<html><body><div class=\"ArticleContentBox\">Naver cafe iframe content with enough public text</div></body></html>",
                "text/html"
        ));
        RenderedHtmlDocumentClient renderedClient = mock(RenderedHtmlDocumentClient.class);
        CafeMaterialContentExtractor extractor = new CafeMaterialContentExtractor(client, renderedClient);

        ExtractedMaterialContent result = extractor.extract(outerUrl);

        assertThat(result.content()).contains("Naver cafe iframe content");
        verify(renderedClient, never()).render(outerUrl);
    }

    @Test
    void notionExtractorRejectsPrivatePage() {
        ExternalHtmlDocumentClient client = client("<html><body>This page is private</body></html>");
        NotionMaterialContentExtractor extractor = new NotionMaterialContentExtractor(client);

        assertThatThrownBy(() -> extractor.extract(URL))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
    }

    @Test
    void notionExtractorUsesRenderedFallbackWhenStaticExtractionIsEmpty() {
        String notionUrl = "https://example.notion.site/page";
        ExternalHtmlDocumentClient client = mock(ExternalHtmlDocumentClient.class);
        when(client.fetch(notionUrl)).thenReturn(new HtmlDocument(
                notionUrl,
                "<html><body><div id=\"notion-app\"></div></body></html>",
                "text/html"
        ));
        RenderedHtmlDocumentClient renderedClient = mock(RenderedHtmlDocumentClient.class);
        when(renderedClient.render(notionUrl)).thenReturn(Optional.of(new HtmlDocument(
                notionUrl,
                "<html><body><div class=\"notion-page-content\">Rendered Notion content with enough public text</div></body></html>",
                "text/html"
        )));
        NotionMaterialContentExtractor extractor = new NotionMaterialContentExtractor(client, renderedClient);

        ExtractedMaterialContent result = extractor.extract(notionUrl);

        assertThat(result.content()).contains("Rendered Notion content");
        verify(renderedClient).render(notionUrl);
    }

    @Test
    void extractorFailsWhenRenderedFallbackIsDisabled() {
        ExternalHtmlDocumentClient client = client("<html><body><main>short</main></body></html>");
        RenderedHtmlDocumentClient renderedClient = new RenderedHtmlDocumentClient(false, 1000, 1000, 1000, 20);
        BlogMaterialContentExtractor extractor = new BlogMaterialContentExtractor(client, renderedClient);

        assertThatThrownBy(() -> extractor.extract(URL))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
    }

    @Test
    void youtubeExtractorUsesTranscriptProviderAndMetadata() {
        ExternalHtmlDocumentClient client = client("""
                <html><head>
                  <meta property="og:title" content="Video Title">
                  <meta property="og:image" content="https://image.example.com/thumb.jpg">
                </head><body></body></html>
                """);
        YoutubeTranscriptProvider transcriptProvider = url -> Optional.of("transcript text from official provider boundary");
        YoutubeMaterialContentExtractor extractor = new YoutubeMaterialContentExtractor(client, transcriptProvider);

        ExtractedMaterialContent result = extractor.extract("https://youtube.com/watch?v=1");

        assertThat(result.platformType()).isEqualTo(PlatformType.YOUTUBE);
        assertThat(result.title()).isEqualTo("Video Title");
        assertThat(result.thumbnailUrl()).isEqualTo("https://image.example.com/thumb.jpg");
        assertThat(result.content()).isEqualTo("transcript text from official provider boundary");
    }

    @Test
    void youtubeExtractorFailsWhenTranscriptIsMissing() {
        ExternalHtmlDocumentClient client = client("<html><head><meta property=\"og:title\" content=\"Video\"></head></html>");
        YoutubeMaterialContentExtractor extractor = new YoutubeMaterialContentExtractor(client, url -> Optional.empty());

        assertThatThrownBy(() -> extractor.extract("https://youtube.com/watch?v=1"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
    }

    @Test
    void youtubeExtractorFailsWhenTranscriptIsBlank() {
        ExternalHtmlDocumentClient client = client("<html><head><meta property=\"og:title\" content=\"Video\"></head></html>");
        YoutubeMaterialContentExtractor extractor = new YoutubeMaterialContentExtractor(client, url -> Optional.of("   "));

        assertThatThrownBy(() -> extractor.extract("https://youtube.com/watch?v=1"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
    }

    @Test
    void youtubeExtractorDoesNotUseTitleAsTranscriptFallback() {
        ExternalHtmlDocumentClient client = client("""
                <html><head>
                  <meta property="og:title" content="Video Title">
                  <meta name="description" content="Video description">
                </head><body></body></html>
                """);
        YoutubeMaterialContentExtractor extractor = new YoutubeMaterialContentExtractor(client, url -> Optional.empty());

        assertThatThrownBy(() -> extractor.extract("https://youtube.com/watch?v=1"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
    }

    @Test
    void extractorFailsWhenContentIsBlank() {
        ExternalHtmlDocumentClient client = client("<html><body><article>   </article></body></html>");
        BlogMaterialContentExtractor extractor = new BlogMaterialContentExtractor(client);

        assertThatThrownBy(() -> extractor.extract(URL))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
    }

    private ExternalHtmlDocumentClient client(String body) {
        ExternalHtmlDocumentClient client = mock(ExternalHtmlDocumentClient.class);
        when(client.fetch(URL)).thenReturn(new HtmlDocument(URL, body, "text/html"));
        when(client.fetch("https://youtube.com/watch?v=1"))
                .thenReturn(new HtmlDocument("https://youtube.com/watch?v=1", body, "text/html"));
        return client;
    }
}
