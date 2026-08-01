package com.teaching.backend.domain.notification.enums;

import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import lombok.Getter;

@Getter
public enum NotificationType {

    SHORT_CUT("Short-Cut"),
    DEEP_DIVE("Deep-Dive");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public static NotificationType fromTeachingMapType(TeachingMapType teachingMapType) {
        return switch (teachingMapType) {
            case SHORTCUT -> SHORT_CUT;
            case DEEPDIVE -> DEEP_DIVE;
            case ALL -> throw new IllegalArgumentException("ALL type cannot create a reminder notification.");
        };
    }
}
