package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotionMaterialContentExtractor extends AbstractHtmlMaterialContentExtractor {

    @Autowired
    public NotionMaterialContentExtractor(
            ExternalHtmlDocumentClient htmlDocumentClient,
            RenderedHtmlDocumentClient renderedHtmlDocumentClient
    ) {
        super(PlatformType.NOTION, htmlDocumentClient, renderedHtmlDocumentClient);
    }

    public NotionMaterialContentExtractor(ExternalHtmlDocumentClient htmlDocumentClient) {
        super(PlatformType.NOTION, htmlDocumentClient);
    }

    @Override
    protected void validateDocument(HtmlDocument document) {
        String body = document.body();
        if (body.contains("This page is private") || body.contains("You do not have access")) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }
    }

    @Override
    protected List<String> contentClassSignals() {
        return List.of("notion-page-content", "notion-page-block", "notion");
    }
}
