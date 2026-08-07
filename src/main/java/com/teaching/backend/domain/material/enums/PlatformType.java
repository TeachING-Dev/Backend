package com.teaching.backend.domain.material.enums;

import lombok.Getter;

@Getter
public enum PlatformType {
    YOUTUBE("youtube-icon.svg"),
    VELOG("velog-icon.svg"),
    TISTORY("tistory-icon.svg"),
    NAVER_BLOG("naver-blog-icon.svg"),
    BLOG("blog-icon.svg"),
    CAFE("cafe-icon.svg"),
    NOTION("notion-icon.svg"),
    WEB("web-icon.svg");

    private final String iconPath;
    PlatformType(String iconPath) { this.iconPath = iconPath; }
    public String getIconPath() { return "https://teaching-app-static-2026.s3.ap-northeast-2.amazonaws.com/icons/"+iconPath; }
}
