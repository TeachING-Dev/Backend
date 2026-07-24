package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.enums.PlatformType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BlogMaterialContentExtractor extends AbstractHtmlMaterialContentExtractor {

    public BlogMaterialContentExtractor(ExternalHtmlDocumentClient htmlDocumentClient) {
        super(PlatformType.BLOG, htmlDocumentClient);
    }

    @Override
    protected List<String> contentClassSignals() {
        return List.of("post", "article", "entry-content", "contents_style", "se-main-container");
    }
}
