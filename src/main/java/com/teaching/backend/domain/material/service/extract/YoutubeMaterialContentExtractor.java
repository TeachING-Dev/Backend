package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class YoutubeMaterialContentExtractor extends AbstractHtmlMaterialContentExtractor {

    private final YoutubeTranscriptProvider youtubeTranscriptProvider;

    public YoutubeMaterialContentExtractor(
            ExternalHtmlDocumentClient htmlDocumentClient,
            YoutubeTranscriptProvider youtubeTranscriptProvider
    ) {
        super(PlatformType.YOUTUBE, htmlDocumentClient);
        this.youtubeTranscriptProvider = youtubeTranscriptProvider;
    }

    @Override
    public ExtractedMaterialContent extract(String originalUrl) {
        ParsedHtmlContent metadata = parse(originalUrl);
        String transcript = youtubeTranscriptProvider.getTranscript(originalUrl)
                .orElseThrow(() -> new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EMPTY));

        if (transcript.isBlank()) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
        }

        return new ExtractedMaterialContent(
                originalUrl,
                PlatformType.YOUTUBE,
                metadata.title(),
                transcript.trim(),
                metadata.thumbnailUrl(),
                metadata.author(),
                metadata.publishedAt()
        );
    }

    @Override
    protected List<String> contentClassSignals() {
        return List.of("watch-title", "title");
    }
}
