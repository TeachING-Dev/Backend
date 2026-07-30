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
        HtmlDocument document = htmlDocumentClient.fetch(originalUrl);
        PlatformType classifiedPlatformType = htmlPlatformClassifier.classify(originalUrl, document.body())
                .orElse(PlatformType.WEB);
        ParsedHtmlContent parsed = parse(originalUrl, document);

        if (!hasSufficientContent(parsed)) {
            parsed = renderedHtmlDocumentClient == null
                    ? emptyParsed(originalUrl)
                    : renderedHtmlDocumentClient.render(originalUrl)
                    .map(renderedDocument -> parse(originalUrl, renderedDocument))
                    .orElseGet(() -> emptyParsed(originalUrl));
        }

        if (!hasSufficientContent(parsed)) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
        }

        return new ExtractedMaterialContent(
                originalUrl,
                classifiedPlatformType,
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
}
