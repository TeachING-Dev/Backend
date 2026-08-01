package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.net.URI;

@Slf4j
public abstract class AbstractHtmlMaterialContentExtractor implements MaterialContentExtractor {

    private static final int MIN_CONTENT_LENGTH = 20;

    private final PlatformType platformType;
    private final ExternalHtmlDocumentClient htmlDocumentClient;
    private final RenderedHtmlDocumentClient renderedHtmlDocumentClient;
    private final HtmlContentParser htmlContentParser = new HtmlContentParser();

    protected AbstractHtmlMaterialContentExtractor(
            PlatformType platformType,
            ExternalHtmlDocumentClient htmlDocumentClient
    ) {
        this(platformType, htmlDocumentClient, null);
    }

    protected AbstractHtmlMaterialContentExtractor(
            PlatformType platformType,
            ExternalHtmlDocumentClient htmlDocumentClient,
            RenderedHtmlDocumentClient renderedHtmlDocumentClient
    ) {
        this.platformType = platformType;
        this.htmlDocumentClient = htmlDocumentClient;
        this.renderedHtmlDocumentClient = renderedHtmlDocumentClient;
    }

    @Override
    public boolean supports(PlatformType platformType) {
        return this.platformType == platformType;
    }

    @Override
    public ExtractedMaterialContent extract(String originalUrl) {
        HtmlDocument document = htmlDocumentClient.fetch(originalUrl);
        validateDocument(document);

        ParsedHtmlContent parsed = parseDocument(originalUrl, document);
        if (!hasSufficientContent(parsed)) {
            log.info(
                    "Static HTML extraction produced insufficient content. platformType={}, url={}, contentLength={}",
                    platformType,
                    safeUrl(originalUrl),
                    parsed == null || parsed.content() == null ? 0 : parsed.content().length()
            );
            parsed = tryPlatformFallback(originalUrl, document)
                    .filter(this::hasSufficientContent)
                    .orElseGet(() -> parseRenderedFallback(originalUrl));
        }
        validateContent(parsed);

        return new ExtractedMaterialContent(
                originalUrl,
                platformType,
                parsed.title(),
                parsed.content(),
                parsed.thumbnailUrl(),
                parsed.author(),
                parsed.publishedAt()
        );
    }

    protected ParsedHtmlContent parse(String originalUrl) {
        HtmlDocument document = htmlDocumentClient.fetch(originalUrl);
        validateDocument(document);

        return parseDocument(originalUrl, document);
    }

    protected ParsedHtmlContent parseDocument(String originalUrl, HtmlDocument document) {
        return htmlContentParser.parse(
                originalUrl,
                document.body(),
                contentClassSignals()
        );
    }

    protected void validateDocument(HtmlDocument document) {
    }

    protected List<String> contentClassSignals() {
        return List.of();
    }

    protected Optional<ParsedHtmlContent> parsePlatformFallback(
            String originalUrl,
            HtmlDocument initialDocument
    ) {
        return Optional.empty();
    }

    protected Optional<HtmlDocument> fetchHtmlDocument(String originalUrl) {
        return Optional.of(htmlDocumentClient.fetch(originalUrl));
    }

    protected HtmlContentParser htmlContentParser() {
        return htmlContentParser;
    }

    protected boolean hasSufficientContent(ParsedHtmlContent parsed) {
        return parsed != null
                && parsed.content() != null
                && !parsed.content().isBlank()
                && parsed.content().trim().length() >= MIN_CONTENT_LENGTH;
    }

    protected PlatformType platformType() {
        return platformType;
    }

    private Optional<ParsedHtmlContent> tryPlatformFallback(
            String originalUrl,
            HtmlDocument initialDocument
    ) {
        try {
            return parsePlatformFallback(originalUrl, initialDocument);
        } catch (MaterialException e) {
            log.warn(
                    "Platform HTML fallback failed. platformType={}, url={}, errorCode={}",
                    platformType,
                    originalUrl,
                    e.getErrorCode() == null ? null : e.getErrorCode().getCode()
            );
            return Optional.empty();
        }
    }

    private ParsedHtmlContent parseRenderedFallback(String originalUrl) {
        if (renderedHtmlDocumentClient == null) {
            return emptyParsed(originalUrl);
        }

        String safeUrl = safeUrl(originalUrl);
        log.info("Trying rendered HTML fallback. platformType={}, url={}", platformType, safeUrl);
        return renderedHtmlDocumentClient.render(originalUrl)
                .map(document -> {
                    validateDocument(document);
                    ParsedHtmlContent parsed = parseDocument(originalUrl, document);
                    log.info(
                            "Rendered HTML fallback parsed. platformType={}, url={}, contentLength={}",
                            platformType,
                            safeUrl,
                            parsed.content() == null ? 0 : parsed.content().length()
                    );
                    return parsed;
                })
                .orElseGet(() -> emptyParsed(originalUrl));
    }

    private ParsedHtmlContent emptyParsed(String originalUrl) {
        return new ParsedHtmlContent(originalUrl, null, "", null, null, null);
    }

    private void validateContent(ParsedHtmlContent parsed) {
        if (!hasSufficientContent(parsed)) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
        }
    }

    private String safeUrl(String originalUrl) {
        try {
            URI uri = URI.create(originalUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            return scheme + "://" + host + path;
        } catch (RuntimeException e) {
            return "[invalid-url]";
        }
    }
}
