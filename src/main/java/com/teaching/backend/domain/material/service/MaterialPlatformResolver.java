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
        if (isDomainOrSubdomain(normalizedHost, "velog.io")) {
            return PlatformType.VELOG;
        }
        if (isDomainOrSubdomain(normalizedHost, "tistory.com")) {
            return PlatformType.TISTORY;
        }
        if (isNaverBlogHost(normalizedHost)) {
            return PlatformType.NAVER_BLOG;
        }
        if (isDomainOrSubdomain(normalizedHost, "notion.so")
                || isDomainOrSubdomain(normalizedHost, "notion.site")) {
            return PlatformType.NOTION;
        }
        if (isCafeHost(normalizedHost)) {
            return PlatformType.WEB;
        }
        if (isBlogHost(normalizedHost)) {
            return PlatformType.BLOG;
        }
        return PlatformType.WEB;
    }

    private boolean isDomainOrSubdomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private boolean isBlogHost(String host) {
        return isDomainOrSubdomain(host, "blogspot.com")
                || isDomainOrSubdomain(host, "medium.com")
                || isDomainOrSubdomain(host, "wordpress.com");
    }

    private boolean isNaverBlogHost(String host) {
        return isDomainOrSubdomain(host, "blog.naver.com");
    }

    private boolean isCafeHost(String host) {
        return isDomainOrSubdomain(host, "cafe.naver.com")
                || isDomainOrSubdomain(host, "cafe.daum.net");
    }
}
