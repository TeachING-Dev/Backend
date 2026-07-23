package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.enums.PlatformType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class MaterialPlatformResolver {

    public PlatformType resolve(PlatformType requestedPlatformType, String originalUrl) {
        if (requestedPlatformType != null) {
            return requestedPlatformType;
        }

        URI uri;
        try {
            uri = URI.create(originalUrl);
        } catch (IllegalArgumentException e) {
            return PlatformType.WEB;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return PlatformType.WEB;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (isDomainOrSubdomain(normalizedHost, "youtube.com")
                || normalizedHost.equals("youtu.be")) {
            return PlatformType.YOUTUBE;
        }
        if (isDomainOrSubdomain(normalizedHost, "notion.so")) {
            return PlatformType.NOTION;
        }

        String path = uri.getPath();
        if (path != null && path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return PlatformType.PDF;
        }
        return PlatformType.WEB;
    }

    private boolean isDomainOrSubdomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
