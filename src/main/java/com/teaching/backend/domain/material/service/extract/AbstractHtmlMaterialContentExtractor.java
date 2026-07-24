package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;

import java.util.List;

public abstract class AbstractHtmlMaterialContentExtractor implements MaterialContentExtractor {

    private static final int MIN_CONTENT_LENGTH = 20;

    private final PlatformType platformType;
    private final ExternalHtmlDocumentClient htmlDocumentClient;
    private final HtmlContentParser htmlContentParser = new HtmlContentParser();

    protected AbstractHtmlMaterialContentExtractor(
            PlatformType platformType,
            ExternalHtmlDocumentClient htmlDocumentClient
    ) {
        this.platformType = platformType;
        this.htmlDocumentClient = htmlDocumentClient;
    }

    @Override
    public boolean supports(PlatformType platformType) {
        return this.platformType == platformType;
    }

    @Override
    public ExtractedMaterialContent extract(String originalUrl) {
        ParsedHtmlContent parsed = parse(originalUrl);
        if (parsed.content() == null || parsed.content().isBlank() || parsed.content().length() < MIN_CONTENT_LENGTH) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
        }

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

    protected HtmlContentParser htmlContentParser() {
        return htmlContentParser;
    }
}
