package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.enums.PlatformType;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MaterialPlatformResolver {

    public PlatformType resolve(PlatformType requestedPlatformType, String originalUrl) {
        if (requestedPlatformType != null) {
            return requestedPlatformType;
        }

        String url = originalUrl.toLowerCase(Locale.ROOT);
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            return PlatformType.YOUTUBE;
        }
        if (url.contains("notion.so")) {
            return PlatformType.NOTION;
        }
        if (url.endsWith(".pdf")) {
            return PlatformType.PDF;
        }
        return PlatformType.WEB;
    }
}
