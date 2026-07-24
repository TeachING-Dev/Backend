package com.teaching.backend.domain.teachingmap.enums;

import lombok.Getter;

@Getter
public enum TeachingMapStatus {

    IN_PROGRESS("진행중"),
    FINISHED("완료"),
    TEMPORARY("임시저장");

    private final String description;

    TeachingMapStatus(String description) {
        this.description = description;
    }
}
