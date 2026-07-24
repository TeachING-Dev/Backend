package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GenericWebMaterialContentExtractor implements MaterialContentExtractor {

    private static final int MIN_CONTENT_LENGTH = 20;

    private final ExternalHtmlDocumentClient htmlDocumentClient;
    private final HtmlPlatformClassifier htmlPlatformClassifier;
    private final HtmlContentParser htmlContentParser = new HtmlContentParser();

    public GenericWebMaterialContentExtractor(
            ExternalHtmlDocumentClient htmlDocumentClient,
            HtmlPlatformClassifier htmlPlatformClassifier
    ) {
        this.htmlDocumentClient = htmlDocumentClient;
        this.htmlPlatformClassifier = htmlPlatformClassifier;
    }

    @Override
    public boolean supports(PlatformType platformType) {
        return platformType == PlatformType.WEB;
    }

    @Override
    public ExtractedMaterialContent extract(String originalUrl) {
        HtmlDocument document = htmlDocumentClient.fetch(originalUrl);
        PlatformType classifiedPlatformType = htmlPlatformClassifier.classify(originalUrl, document.body())
                .orElseThrow(() -> new MaterialException(MaterialErrorCode.UNSUPPORTED_MATERIAL_PLATFORM));
        ParsedHtmlContent parsed = htmlContentParser.parse(
                originalUrl,
                document.body(),
                contentClassSignals()
        );

        if (parsed.content() == null || parsed.content().isBlank() || parsed.content().length() < MIN_CONTENT_LENGTH) {
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
}
