package com.teaching.backend.domain.material.enums;

import lombok.Getter;

@Getter
public enum PlatformType {
    YOUTUBE("youtube-icon.svg"),
    VELOG("velog-icon.svg"),
    BLOG("blog-icon.svg"),
    CAFE("cafe-icon.svg"),
    NOTION("notion-icon.svg"),
    PDF("pdf-icon.svg"),
    WEB("web-icon.svg");

    private final String iconPath;
    PlatformType(String iconPath) { this.iconPath = iconPath; }
    public String getIconPath() { return iconPath; }
}
