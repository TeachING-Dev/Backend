package com.teaching.backend.domain.material.enums;

public enum MaterialAiStageType {
    CONTENT_ANALYSIS(1);

    private final int order;

    MaterialAiStageType(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }
}
