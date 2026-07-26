package com.teaching.backend.domain.material.service.extract;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlContentParser {

    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "(?is)<(script|style|noscript)[^>]*>.*?</\\1>"
    );
    private static final Pattern NOISE_BLOCK_PATTERN = Pattern.compile(
            "(?is)<(nav|footer|aside)[^>]*>.*?</\\1>"
    );
    private static final Pattern NOISE_CLASS_PATTERN = Pattern.compile(
            "(?is)<([a-z0-9]+)\\b(?=[^>]*(class|id)\\s*=\\s*(['\"])[^'\"]*(advertisement|ad-|ads-|ad_|ads_|banner|sponsor|comment|reply|menu|hidden|login|follow|share|sidebar|recommend|related|pagination|prev|next|toc)[^'\"]*\\3)[^>]*>.*?</\\1>"
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
                metaContent(cleanedHtml, "itemprop", "datePublished"),
                firstAttribute(cleanedHtml, "time", "datetime")
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
        List<String> signalCandidates = new ArrayList<>();
        for (String signal : contentClassSignals) {
            for (String tag : List.of("article", "main", "section", "div")) {
                signalCandidates.addAll(tagsWithClassContaining(html, tag, signal));
            }
        }

        Optional<String> signalContent = longestTextCandidate(signalCandidates);
        if (signalContent.isPresent()) {
            return signalContent;
        }

        List<String> candidates = new ArrayList<>();
        tagHtml(html, "article").ifPresent(candidates::add);
        tagHtml(html, "main").ifPresent(candidates::add);

        return longestTextCandidate(candidates);
    }

    private Optional<String> longestTextCandidate(List<String> candidates) {
        return candidates.stream()
                .map(this::normalizeBlank)
                .flatMap(Optional::stream)
                .max((left, right) -> Integer.compare(htmlToText(left).length(), htmlToText(right).length()));
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

    private Optional<String> firstAttribute(String html, String tag, String attributeName) {
        Pattern pattern = Pattern.compile(
                "(?is)<" + tag + "\\b(?=[^>]*\\b" + Pattern.quote(attributeName) +
                        "\\s*=\\s*(['\"])(.*?)\\1)[^>]*>"
        );
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return normalizeBlank(decodeHtml(matcher.group(2)));
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

    private List<String> tagsWithClassContaining(String html, String tag, String classSignal) {
        Pattern pattern = Pattern.compile(
                "(?is)<" + tag + "\\b(?=[^>]*\\bclass\\s*=\\s*(['\"])[^'\"]*" +
                        Pattern.quote(classSignal) + "[^'\"]*\\1)[^>]*>(.*?)</" + tag + ">"
        );
        Matcher matcher = pattern.matcher(html);
        List<String> sections = new ArrayList<>();
        while (matcher.find()) {
            normalizeBlank(matcher.group(2)).ifPresent(sections::add);
        }

        return sections;
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
        return removeUiOnlyLines(normalizedLines.replaceAll("(?m)^ +| +$", ""));
    }

    private String removeUiOnlyLines(String text) {
        String[] lines = text.split("\\n", -1);
        List<String> keptLines = new ArrayList<>();
        for (String line : lines) {
            if (!isUiOnlyLine(line.trim())) {
                keptLines.add(line);
            }
        }
        return String.join("\n", keptLines)
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private boolean isUiOnlyLine(String line) {
        if (line.isBlank() || line.length() > 30) {
            return false;
        }

        return line.equals("\uB85C\uADF8\uC778")
                || line.equals("\uD314\uB85C\uC6B0")
                || line.equals("\uACF5\uC720")
                || line.equals("\uB313\uAE00 \uC791\uC131")
                || line.equals("\uC774\uC804 \uD3EC\uC2A4\uD2B8")
                || line.equals("\uB2E4\uC74C \uD3EC\uC2A4\uD2B8")
                || line.matches("\uB313\uAE00\\s*\\d*")
                || line.matches("\\d+\uAC1C\uC758 \uB313\uAE00");
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
