package com.teaching.backend.domain.material.service.extract;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlContentParser {

    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "(?is)<(script|style)[^>]*>.*?</\\1>"
    );
    private static final Pattern NOISE_BLOCK_PATTERN = Pattern.compile(
            "(?is)<(nav|header|footer|aside)[^>]*>.*?</\\1>"
    );
    private static final Pattern NOISE_CLASS_PATTERN = Pattern.compile(
            "(?is)<([a-z0-9]+)\\b(?=[^>]*(class|id)\\s*=\\s*(['\"])[^'\"]*(ad|ads|advertisement|comment|reply|menu|hidden|login)[^'\"]*\\3)[^>]*>.*?</\\1>"
    );
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?is)<!--.*?-->");
    private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");

    public ParsedHtmlContent parse(String originalUrl, String html, List<String> contentClassSignals) {
        String cleanedHtml = removeNoise(html);
        String title = firstNonBlank(
                metaContent(cleanedHtml, "property", "og:title"),
                firstTagText(cleanedHtml, "article", "h1"),
                firstTagText(cleanedHtml, "main", "h1"),
                tagText(cleanedHtml, "h1"),
                tagText(cleanedHtml, "title")
        ).orElse(null);
        String thumbnailUrl = firstNonBlank(
                metaContent(cleanedHtml, "property", "og:image"),
                metaContent(cleanedHtml, "name", "twitter:image")
        ).orElse(null);
        String author = firstNonBlank(
                metaContent(cleanedHtml, "name", "author"),
                metaContent(cleanedHtml, "property", "article:author")
        ).orElse(null);
        LocalDateTime publishedAt = firstNonBlank(
                metaContent(cleanedHtml, "property", "article:published_time"),
                metaContent(cleanedHtml, "name", "date"),
                metaContent(cleanedHtml, "itemprop", "datePublished")
        ).flatMap(this::parseDateTime).orElse(null);

        String contentHtml = findContentHtml(cleanedHtml, contentClassSignals)
                .orElse(cleanedHtml);
        String content = htmlToText(contentHtml);

        return new ParsedHtmlContent(
                originalUrl,
                title,
                content,
                thumbnailUrl,
                author,
                publishedAt
        );
    }

    private Optional<String> findContentHtml(String html, List<String> contentClassSignals) {
        for (String signal : contentClassSignals) {
            Optional<String> section = tagWithClassContaining(html, "div", signal);
            if (section.isPresent()) {
                return section;
            }
        }

        return firstNonBlank(
                tagHtml(html, "article"),
                tagHtml(html, "main")
        );
    }

    private String removeNoise(String html) {
        String result = COMMENT_PATTERN.matcher(html).replaceAll(" ");
        result = SCRIPT_STYLE_PATTERN.matcher(result).replaceAll(" ");
        result = NOISE_BLOCK_PATTERN.matcher(result).replaceAll(" ");
        return NOISE_CLASS_PATTERN.matcher(result).replaceAll(" ");
    }

    private Optional<String> metaContent(String html, String attributeName, String attributeValue) {
        Pattern pattern = Pattern.compile(
                "(?is)<meta\\b(?=[^>]*\\b" + Pattern.quote(attributeName) + "\\s*=\\s*(['\"])" +
                        Pattern.quote(attributeValue) + "\\1)(?=[^>]*\\bcontent\\s*=\\s*(['\"])(.*?)\\2)[^>]*>"
        );
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return normalizeBlank(decodeHtml(matcher.group(3)));
        }

        return Optional.empty();
    }

    private Optional<String> firstTagText(String html, String parentTag, String childTag) {
        return tagHtml(html, parentTag)
                .flatMap(parentHtml -> tagText(parentHtml, childTag));
    }

    private Optional<String> tagText(String html, String tag) {
        return tagHtml(html, tag)
                .map(this::htmlToText)
                .flatMap(this::normalizeBlank);
    }

    private Optional<String> tagHtml(String html, String tag) {
        Pattern pattern = Pattern.compile("(?is)<" + tag + "\\b[^>]*>(.*?)</" + tag + ">");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return normalizeBlank(matcher.group(1));
        }

        return Optional.empty();
    }

    private Optional<String> tagWithClassContaining(String html, String tag, String classSignal) {
        Pattern pattern = Pattern.compile(
                "(?is)<" + tag + "\\b(?=[^>]*\\bclass\\s*=\\s*(['\"])[^'\"]*" +
                        Pattern.quote(classSignal) + "[^'\"]*\\1)[^>]*>(.*?)</" + tag + ">"
        );
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return normalizeBlank(matcher.group(2));
        }

        return Optional.empty();
    }

    @SafeVarargs
    private Optional<String> firstNonBlank(Optional<String>... candidates) {
        for (Optional<String> candidate : candidates) {
            if (candidate.isPresent()) {
                return candidate;
            }
        }

        return Optional.empty();
    }

    private Optional<LocalDateTime> parseDateTime(String value) {
        try {
            return Optional.of(OffsetDateTime.parse(value).toLocalDateTime());
        } catch (RuntimeException ignored) {
            try {
                return Optional.of(LocalDateTime.parse(value));
            } catch (RuntimeException ignoredAgain) {
                return Optional.empty();
            }
        }
    }

    private String htmlToText(String html) {
        String withParagraphBreaks = html
                .replaceAll("(?is)</(p|div|section|article|main|h1|h2|h3|li|br)>", "\n")
                .replaceAll("(?is)<br\\s*/?>", "\n");
        String withoutTags = TAG_PATTERN.matcher(withParagraphBreaks).replaceAll(" ");
        String decoded = decodeHtml(withoutTags);
        return normalizeText(decoded);
    }

    private String normalizeText(String text) {
        String normalizedLines = text.replace('\r', '\n')
                .replaceAll("[\\t ]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return normalizedLines.replaceAll("(?m)^ +| +$", "");
    }

    private Optional<String> normalizeBlank(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String normalized = value.trim();
        return normalized.isBlank() ? Optional.empty() : Optional.of(normalized);
    }

    private String decodeHtml(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    public boolean looksLikeArticle(String html) {
        String lowerHtml = html.toLowerCase(Locale.ROOT);
        return lowerHtml.contains("<article")
                || lowerHtml.contains("og:type\" content=\"article")
                || lowerHtml.contains("og:type' content='article")
                || lowerHtml.contains("datepublished")
                || lowerHtml.contains("article:published_time");
    }
}
