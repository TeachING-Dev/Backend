package com.teaching.backend.teachingmap.enums;

import lombok.Getter;

@Getter
public enum TeachingMapType {

    SHORTCUT("숏컷"),
    DEEPDIVE("딥다이브");

    private final String description;

    TeachingMapType(String description) {
        this.description = description;
    }
}
