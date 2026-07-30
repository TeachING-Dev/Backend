package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.enums.PlatformType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VelogMaterialContentExtractor extends AbstractHtmlMaterialContentExtractor {

    @Autowired
    public VelogMaterialContentExtractor(
            ExternalHtmlDocumentClient htmlDocumentClient,
            RenderedHtmlDocumentClient renderedHtmlDocumentClient
    ) {
        super(PlatformType.VELOG, htmlDocumentClient, renderedHtmlDocumentClient);
    }

    public VelogMaterialContentExtractor(ExternalHtmlDocumentClient htmlDocumentClient) {
        super(PlatformType.VELOG, htmlDocumentClient);
    }

    @Override
    protected List<String> contentClassSignals() {
        return List.of("velog", "markdown", "content");
    }
}
