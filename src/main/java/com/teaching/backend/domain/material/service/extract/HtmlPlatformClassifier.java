package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.enums.PlatformType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

@Component
public class HtmlPlatformClassifier {

    public Optional<PlatformType> classify(
            String originalUrl,
            String html
    ) {
        String host = resolveHost(originalUrl);
        String lowerHtml = html == null ? "" : html.toLowerCase(Locale.ROOT);
        String path = resolvePath(originalUrl);

        if (isKnownCafeHost(host)) {
            return Optional.of(PlatformType.CAFE);
        }

        if (isKnownBlogHost(host) || blogScore(lowerHtml, path) >= 3) {
            return Optional.of(PlatformType.BLOG);
        }

        if (cafeScore(lowerHtml, path) >= 3) {
            return Optional.of(PlatformType.CAFE);
        }

        return Optional.empty();
    }

    private int blogScore(String html, String path) {
        int score = 0;
        if (html.contains("og:type") && html.contains("article")) {
            score++;
        }
        if (html.contains("<article")) {
            score++;
        }
        if (html.contains("wordpress") || html.contains("blogger") || html.contains("tistory")) {
            score += 2;
        }
        if (path.contains("/post") || path.contains("/article") || path.contains("/entry")) {
            score += 2;
        }
        return score;
    }

    private int cafeScore(String html, String path) {
        int score = 0;
        if (html.contains("cafe") || html.contains("community") || html.contains("board")
                || path.contains("/board") || path.contains("/community") || path.contains("/cafe")) {
            score += 2;
        }
        if (html.contains("clubid") || html.contains("articleid") || html.contains("member-only")) {
            score += 2;
        }
        if (html.contains("comment") || html.contains("reply")) {
            score++;
        }
        return score;
    }

    private boolean isKnownBlogHost(String host) {
        return isDomainOrSubdomain(host, "blog.naver.com")
                || isDomainOrSubdomain(host, "tistory.com")
                || isDomainOrSubdomain(host, "blogspot.com")
                || isDomainOrSubdomain(host, "medium.com")
                || isDomainOrSubdomain(host, "wordpress.com");
    }

    private boolean isKnownCafeHost(String host) {
        return isDomainOrSubdomain(host, "cafe.naver.com")
                || isDomainOrSubdomain(host, "cafe.daum.net");
    }

    private boolean isDomainOrSubdomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private String resolveHost(String originalUrl) {
        try {
            String host = URI.create(originalUrl).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String resolvePath(String originalUrl) {
        try {
            String path = URI.create(originalUrl).getPath();
            return path == null ? "" : path.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
