package com.teaching.backend.domain.notification.enums;

import lombok.Getter;

@Getter
public enum NotificationTargetType {
    TEACHING_MAP("티칭맵"),
    MATERIAL("자료"),
    FOLDER("폴더");

    private final String description;

    NotificationTargetType(String description) {
        this.description = description;
    }
}
