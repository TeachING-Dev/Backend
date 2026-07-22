package com.teaching.backend.domain.term.dto;

import com.teaching.backend.domain.term.entity.Term;

public record TermResponse(
        Long termId,
        String title,
        String content,
        Boolean isRequired,
        String version
) {
    public static TermResponse from(Term term) {
        return new TermResponse(
                term.getId(),
                term.getTitle(),
                term.getContent(),
                term.getIsRequired(),
                term.getVersion()
        );
    }
}