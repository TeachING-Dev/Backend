package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.enums.PlatformType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VelogMaterialContentExtractor extends AbstractHtmlMaterialContentExtractor {

    public VelogMaterialContentExtractor(ExternalHtmlDocumentClient htmlDocumentClient) {
        super(PlatformType.VELOG, htmlDocumentClient);
    }

    @Override
    protected List<String> contentClassSignals() {
        return List.of("velog", "markdown", "content");
    }
}
