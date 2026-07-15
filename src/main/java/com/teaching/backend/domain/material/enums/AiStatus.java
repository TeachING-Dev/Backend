package com.teaching.backend.domain.material.enums;

import lombok.Getter;

@Getter
public enum AiStatus {

    PENDING("대기"),
    CRAWLING("파싱중"),
    ANALYZING("AI 분석중"),
    COMPLETED("완료"),
    FAILED("실패"),
    MANUAL_SAVED("수동저장");

    private final String description;

    AiStatus(String description) {
        this.description = description;
    }
}
