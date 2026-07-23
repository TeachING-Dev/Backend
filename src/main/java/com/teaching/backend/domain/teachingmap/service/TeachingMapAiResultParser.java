package com.teaching.backend.domain.teachingmap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapErrorCode;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapException;
import com.teaching.backend.global.exception.GeneralException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TeachingMapAiResultParser {

    public record TeachingMapAiResult(String mode, List<TeachingMapAiNode> nodes) {}
    public record TeachingMapAiNode(int step, Long materialId, String title, String aiGuide) {}

    private final JsonMapper jsonMapper;

    public TeachingMapAiResultParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public TeachingMapAiResult parse(String aiResponse) {
        try {
            return jsonMapper.readValue(aiResponse, TeachingMapAiResult.class);
        } catch (JacksonException e) {
            throw new GeneralException(TeachingMapErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
    }
}