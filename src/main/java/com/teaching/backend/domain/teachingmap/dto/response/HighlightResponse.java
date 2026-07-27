package com.teaching.backend.domain.teachingmap.dto.response;


import com.teaching.backend.domain.material.entity.MaterialHighlight;

public record HighlightResponse(Long highlightId,String text, String type) {
    public static HighlightResponse from(MaterialHighlight highlight) {
        return new HighlightResponse(highlight.getId(), highlight.getHighlightText(), highlight.getHighlightType().name());
    }
}