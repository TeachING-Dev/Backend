package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GenericWebMaterialContentExtractor implements MaterialContentExtractor {

    private static final int MIN_CONTENT_LENGTH = 20;

    private final ExternalHtmlDocumentClient htmlDocumentClient;
    private final HtmlPlatformClassifier htmlPlatformClassifier;
    private final RenderedHtmlDocumentClient renderedHtmlDocumentClient;
    private final HtmlContentParser htmlContentParser = new HtmlContentParser();

    @Autowired
    public GenericWebMaterialContentExtractor(
            ExternalHtmlDocumentClient htmlDocumentClient,
            HtmlPlatformClassifier htmlPlatformClassifier,
            RenderedHtmlDocumentClient renderedHtmlDocumentClient
    ) {
        this.htmlDocumentClient = htmlDocumentClient;
        this.htmlPlatformClassifier = htmlPlatformClassifier;
        this.renderedHtmlDocumentClient = renderedHtmlDocumentClient;
    }

    public GenericWebMaterialContentExtractor(
            ExternalHtmlDocumentClient htmlDocumentClient,
            HtmlPlatformClassifier htmlPlatformClassifier
    ) {
        this(htmlDocumentClient, htmlPlatformClassifier, null);
    }

    @Override
    public boolean supports(PlatformType platformType) {
        return platformType == PlatformType.WEB;
    }

    @Override
    public ExtractedMaterialContent extract(String originalUrl) {
        HtmlDocument document;
        try {
            document = htmlDocumentClient.fetch(originalUrl);
        } catch (MaterialException e) {
            if (!isRenderedFallbackAllowed(e)) {
                throw e;
            }
            ParsedHtmlContent renderedParsed = parseRenderedFallback(originalUrl);
            validateContent(renderedParsed, e);
            return content(originalUrl, renderedParsed);
        }

        htmlPlatformClassifier.classify(originalUrl, document.body());
        ParsedHtmlContent parsed = parse(originalUrl, document);

        if (!hasSufficientContent(parsed)) {
            parsed = parseRenderedFallback(originalUrl);
        }

        validateContent(parsed, null);
        return content(originalUrl, parsed);
    }

    private ExtractedMaterialContent content(String originalUrl, ParsedHtmlContent parsed) {
        return new ExtractedMaterialContent(
                originalUrl,
                PlatformType.WEB,
                parsed.title(),
                parsed.content(),
                parsed.thumbnailUrl(),
                parsed.author(),
                parsed.publishedAt()
        );
    }

    private List<String> contentClassSignals() {
        return List.of("post", "article", "entry-content", "content", "board", "community");
    }

    private ParsedHtmlContent parse(String originalUrl, HtmlDocument document) {
        return htmlContentParser.parse(
                originalUrl,
                document.body(),
                contentClassSignals()
        );
    }

    private boolean hasSufficientContent(ParsedHtmlContent parsed) {
        return parsed != null
                && parsed.content() != null
                && !parsed.content().isBlank()
                && parsed.content().trim().length() >= MIN_CONTENT_LENGTH;
    }

    private ParsedHtmlContent emptyParsed(String originalUrl) {
        return new ParsedHtmlContent(originalUrl, null, "", null, null, null);
    }

    private ParsedHtmlContent parseRenderedFallback(String originalUrl) {
        return renderedHtmlDocumentClient == null
                ? emptyParsed(originalUrl)
                : renderedHtmlDocumentClient.render(originalUrl)
                .map(renderedDocument -> parse(originalUrl, renderedDocument))
                .orElseGet(() -> emptyParsed(originalUrl));
    }

    private void validateContent(ParsedHtmlContent parsed, MaterialException staticFetchFailure) {
        if (hasSufficientContent(parsed)) {
            return;
        }
        if (staticFetchFailure == null) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
        }
        MaterialException exception = new MaterialException(
                MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED,
                staticFetchFailure
        );
        exception.addSuppressed(new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EMPTY));
        throw exception;
    }

    private boolean isRenderedFallbackAllowed(MaterialException exception) {
        return exception instanceof HtmlFetchException htmlFetchException
                && htmlFetchException.isRenderedFallbackAllowed()
                && renderedHtmlDocumentClient != null;
    }
}
