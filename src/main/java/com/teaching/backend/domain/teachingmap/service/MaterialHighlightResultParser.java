package com.teaching.backend.domain.teachingmap.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MaterialHighlightResultParser {

    public record MaterialHighlightAiResult(List<HighlightItem> highlights) {}
    public record HighlightItem(
            String text,
            String type
    ) {}

    private final JsonMapper jsonMapper;

    public MaterialHighlightResultParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public List<HighlightItem> parse(String aiResponse) {
        try {
            MaterialHighlightAiResult result =
                    jsonMapper.readValue(aiResponse, MaterialHighlightAiResult.class);
            return result.highlights() != null ? result.highlights() : List.of();
        } catch (JacksonException e) {
            throw new GeneralException(TeachingMapErrorCode.HIGHLIGHT_AI_RESPONSE_PARSE_FAILED);
        }
    }
}