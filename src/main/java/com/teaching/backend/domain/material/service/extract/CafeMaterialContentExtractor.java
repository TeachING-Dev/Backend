package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CafeMaterialContentExtractor extends AbstractHtmlMaterialContentExtractor {

    @Autowired
    public CafeMaterialContentExtractor(
            ExternalHtmlDocumentClient htmlDocumentClient,
            RenderedHtmlDocumentClient renderedHtmlDocumentClient
    ) {
        super(PlatformType.CAFE, htmlDocumentClient, renderedHtmlDocumentClient);
    }

    public CafeMaterialContentExtractor(ExternalHtmlDocumentClient htmlDocumentClient) {
        super(PlatformType.CAFE, htmlDocumentClient);
    }

    @Override
    protected void validateDocument(HtmlDocument document) {
        String body = document.body();
        if (body == null || body.isBlank()) {
            return;
        }
        if (body.contains("\uB85C\uADF8\uC778")
                && (body.contains("\uAD8C\uD55C") || body.contains("\uCE74\uD398 \uD68C\uC6D0"))) {
            log.warn(
                    "Naver Cafe content extraction rejected access-limited page. url={}, bodyLength={}",
                    safeUrl(document.originalUrl()),
                    body.length()
            );
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }
    }

    @Override
    protected Optional<ParsedHtmlContent> parsePlatformFallback(
            String originalUrl,
            HtmlDocument initialDocument
    ) {
        if (!isNaverCafe(originalUrl)) {
            return Optional.empty();
        }

        log.info(
                "Naver Cafe platform fallback started. url={}, outerBodyLength={}",
                safeUrl(originalUrl),
                initialDocument.body() == null ? 0 : initialDocument.body().length()
        );
        return findNaverCafeFrameUrl(originalUrl, initialDocument.body())
                .map(frameUrl -> {
                    log.info("Naver Cafe iframe found. outerUrl={}, iframeUrl={}", safeUrl(originalUrl), safeUrl(frameUrl));
                    return frameUrl;
                })
                .flatMap(this::fetchNaverCafeFrame)
                .map(document -> {
                    validateDocument(document);
                    ParsedHtmlContent parsed = parseDocument(document.originalUrl(), document);
                    log.info(
                            "Naver Cafe iframe parsed. iframeUrl={}, contentLength={}",
                            safeUrl(document.originalUrl()),
                            parsed.content() == null ? 0 : parsed.content().length()
                    );
                    return parsed;
                });
    }

    @Override
    protected List<String> contentClassSignals() {
        return List.of("article_viewer", "ArticleContentBox", "se-main-container", "ContentRenderer", "post");
    }

    private Optional<String> findNaverCafeFrameUrl(String originalUrl, String html) {
        Document document = Jsoup.parse(html == null ? "" : html, originalUrl);
        Element frame = document.selectFirst("iframe#cafe_main, iframe[name=cafe_main], iframe#mainFrame, iframe[name=mainFrame]");
        if (frame == null) {
            log.info("Naver Cafe iframe not found. url={}", safeUrl(originalUrl));
            return Optional.empty();
        }

        String frameUrl = frame.absUrl("src");
        if (frameUrl == null || frameUrl.isBlank() || !isNaverCafe(frameUrl)) {
            log.warn(
                    "Naver Cafe iframe ignored. outerUrl={}, iframeUrl={}",
                    safeUrl(originalUrl),
                    frameUrl == null || frameUrl.isBlank() ? null : safeUrl(frameUrl)
            );
            return Optional.empty();
        }
        return Optional.of(frameUrl);
    }

    private Optional<HtmlDocument> fetchNaverCafeFrame(String frameUrl) {
        try {
            log.info("Naver Cafe iframe fetch started. iframeUrl={}", safeUrl(frameUrl));
            Optional<HtmlDocument> document = fetchHtmlDocument(frameUrl);
            document.ifPresent(htmlDocument -> log.info(
                    "Naver Cafe iframe fetch succeeded. iframeUrl={}, bodyLength={}",
                    safeUrl(frameUrl),
                    htmlDocument.body() == null ? 0 : htmlDocument.body().length()
            ));
            return document;
        } catch (RuntimeException e) {
            Throwable rootCause = rootCause(e);
            log.warn(
                    "Naver Cafe iframe fetch failed. iframeUrl={}, reason={}, message={}, rootCause={}, rootCauseMessage={}",
                    safeUrl(frameUrl),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    rootCause == null ? null : rootCause.getClass().getSimpleName(),
                    rootCause == null ? null : rootCause.getMessage()
            );
            throw e;
        }
    }

    private boolean isNaverCafe(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return normalizedHost.equals("cafe.naver.com") || normalizedHost.endsWith(".cafe.naver.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String safeUrl(String originalUrl) {
        try {
            URI uri = URI.create(originalUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            return scheme + "://" + host + path;
        } catch (IllegalArgumentException e) {
            return "[invalid-url]";
        }
    }
}
