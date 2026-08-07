package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.enums.PlatformType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class BlogMaterialContentExtractor extends AbstractHtmlMaterialContentExtractor {

    @Autowired
    public BlogMaterialContentExtractor(
            ExternalHtmlDocumentClient htmlDocumentClient,
            RenderedHtmlDocumentClient renderedHtmlDocumentClient
    ) {
        super(PlatformType.BLOG, htmlDocumentClient, renderedHtmlDocumentClient);
    }

    public BlogMaterialContentExtractor(ExternalHtmlDocumentClient htmlDocumentClient) {
        super(PlatformType.BLOG, htmlDocumentClient);
    }

    @Override
    public boolean supports(PlatformType platformType) {
        return platformType == PlatformType.BLOG
                || platformType == PlatformType.TISTORY
                || platformType == PlatformType.NAVER_BLOG;
    }

    @Override
    protected Optional<ParsedHtmlContent> parsePlatformFallback(
            String originalUrl,
            HtmlDocument initialDocument
    ) {
        if (!isNaverBlog(originalUrl)) {
            return Optional.empty();
        }

        return findNaverBlogFrameUrl(originalUrl, initialDocument.body())
                .flatMap(this::fetchHtmlDocument)
                .map(document -> parseDocument(document.originalUrl(), document));
    }

    @Override
    protected List<String> contentClassSignals() {
        return List.of(
                "se-main-container",
                "contents_style",
                "article_view",
                "area_view",
                "tt_article_useless_p_margin",
                "entry-content",
                "post-content",
                "article-body",
                "blogview_content",
                "post",
                "article"
        );
    }

    private Optional<String> findNaverBlogFrameUrl(String originalUrl, String html) {
        Document document = Jsoup.parse(html == null ? "" : html, originalUrl);
        Element frame = document.selectFirst("iframe#mainFrame, iframe[name=mainFrame]");
        if (frame == null) {
            return Optional.empty();
        }

        String frameUrl = frame.absUrl("src");
        if (frameUrl == null || frameUrl.isBlank() || !isNaverBlog(frameUrl)) {
            return Optional.empty();
        }
        return Optional.of(frameUrl);
    }

    private boolean isNaverBlog(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return normalizedHost.equals("blog.naver.com") || normalizedHost.endsWith(".blog.naver.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
