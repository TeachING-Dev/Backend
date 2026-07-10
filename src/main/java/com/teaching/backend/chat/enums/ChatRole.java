package com.teaching.backend.chat.enums;

import lombok.Getter;

@Getter
public enum ChatRole {

    USER("사용자"),
    AI("AI");

    private final String description;

    ChatRole(String description) {
        this.description = description;
    }
}
