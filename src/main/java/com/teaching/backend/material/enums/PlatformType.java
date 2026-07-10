package com.teaching.backend.material.enums;

import lombok.Getter;

@Getter
public enum PlatformType {
    YOUTUBE("youtube-icon.svg"),
    BLOG("blog-icon.svg"),
    NOTION("notion-icon.svg"),
    PDF("pdf-icon.svg"),
    WEB("web-icon.svg");

    private final String iconPath;
    PlatformType(String iconPath) { this.iconPath = iconPath; }
    public String getIconPath() { return iconPath; }
}
