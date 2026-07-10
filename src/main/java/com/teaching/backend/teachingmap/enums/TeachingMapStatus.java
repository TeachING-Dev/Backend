package com.teaching.backend.teachingmap.enums;

import lombok.Getter;

@Getter
public enum TeachingMapStatus {

    IN_PROGRESS("진행중"),
    FINISHED("완료");

    private final String description;

    TeachingMapStatus(String description) {
        this.description = description;
    }
}
