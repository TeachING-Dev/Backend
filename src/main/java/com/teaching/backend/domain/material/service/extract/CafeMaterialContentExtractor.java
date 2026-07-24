package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CafeMaterialContentExtractor extends AbstractHtmlMaterialContentExtractor {

    public CafeMaterialContentExtractor(ExternalHtmlDocumentClient htmlDocumentClient) {
        super(PlatformType.CAFE, htmlDocumentClient);
    }

    @Override
    protected void validateDocument(HtmlDocument document) {
        String body = document.body();
        if (body.contains("로그인") && (body.contains("권한") || body.contains("카페 회원"))) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }
    }

    @Override
    protected List<String> contentClassSignals() {
        return List.of("article_viewer", "ArticleContentBox", "se-main-container", "post");
    }
}
