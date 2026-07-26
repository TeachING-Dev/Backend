package com.teaching.backend.domain.material.service.extract;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class HtmlContentParser {

    private static final List<String> CONTENT_TAGS = List.of("article", "main", "section", "div");
    private static final List<String> NOISE_SIGNALS = List.of(
            "advertisement",
            "ad-",
            "ads-",
            "ad_",
            "ads_",
            "banner",
            "sponsor",
            "comment",
            "reply",
            "menu",
            "hidden",
            "login",
            "follow",
            "share",
            "sidebar",
            "recommend",
            "related",
            "pagination",
            "prev",
            "next",
            "toc"
    );
    private static final Set<String> BLOCK_TAGS = Set.of(
            "article",
            "main",
            "section",
            "div",
            "p",
            "li",
            "ul",
            "ol",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "blockquote",
            "pre"
    );

    public ParsedHtmlContent parse(String originalUrl, String html, List<String> contentClassSignals) {
        Document document = Jsoup.parse(html == null ? "" : html, originalUrl);
        removeNoise(document);
        String title = firstNonBlank(
                metaContent(document, "property", "og:title"),
                firstTagText(document, "article", "h1"),
                firstTagText(document, "main", "h1"),
                tagText(document, "h1"),
                tagText(document, "title")
        ).orElse(null);
        String thumbnailUrl = firstNonBlank(
                metaContent(document, "property", "og:image"),
                metaContent(document, "name", "twitter:image")
        ).orElse(null);
        String author = firstNonBlank(
                metaContent(document, "name", "author"),
                metaContent(document, "property", "article:author")
        ).orElse(null);
        LocalDateTime publishedAt = firstNonBlank(
                metaContent(document, "property", "article:published_time"),
                metaContent(document, "name", "date"),
                metaContent(document, "itemprop", "datePublished"),
                firstAttribute(document, "time", "datetime")
        ).flatMap(this::parseDateTime).orElse(null);

        Element contentElement = findContentElement(document, contentClassSignals)
                .orElse(document.body() == null ? document : document.body());
        String content = elementToText(contentElement);

        return new ParsedHtmlContent(
                originalUrl,
                title,
                content,
                thumbnailUrl,
                author,
                publishedAt
        );
    }

    private Optional<Element> findContentElement(Document document, List<String> contentClassSignals) {
        List<Element> signalCandidates = new ArrayList<>();
        for (String signal : contentClassSignals) {
            for (String tag : CONTENT_TAGS) {
                signalCandidates.addAll(tagsWithClassContaining(document, tag, signal));
            }
        }

        Optional<Element> signalContent = longestTextCandidate(signalCandidates);
        if (signalContent.isPresent()) {
            return signalContent;
        }

        List<Element> candidates = new ArrayList<>();
        firstTag(document, "article").ifPresent(candidates::add);
        firstTag(document, "main").ifPresent(candidates::add);

        return longestTextCandidate(candidates);
    }

    private Optional<Element> longestTextCandidate(List<Element> candidates) {
        return candidates.stream()
                .filter(candidate -> normalizeBlank(elementToText(candidate)).isPresent())
                .max((left, right) -> Integer.compare(elementToText(left).length(), elementToText(right).length()));
    }

    private void removeNoise(Document document) {
        document.select("script, style, noscript, nav, footer, aside").remove();
        document.select("[class], [id]").stream()
                .filter(this::hasNoiseSignal)
                .forEach(Element::remove);
    }

    private boolean hasNoiseSignal(Element element) {
        String value = (element.className() + " " + element.id()).toLowerCase(Locale.ROOT);
        return NOISE_SIGNALS.stream().anyMatch(value::contains);
    }

    private Optional<String> metaContent(Document document, String attributeName, String attributeValue) {
        return document.select("meta").stream()
                .filter(meta -> attributeValue.equalsIgnoreCase(meta.attr(attributeName)))
                .map(meta -> meta.attr("content"))
                .map(this::normalizeBlank)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<String> firstAttribute(Document document, String tag, String attributeName) {
        return document.select(tag).stream()
                .map(element -> element.attr(attributeName))
                .map(this::normalizeBlank)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<String> firstTagText(Document document, String parentTag, String childTag) {
        return firstTag(document, parentTag)
                .flatMap(parent -> firstTag(parent, childTag))
                .map(this::elementToText)
                .flatMap(this::normalizeBlank);
    }

    private Optional<String> tagText(Document document, String tag) {
        return firstTag(document, tag)
                .map(this::elementToText)
                .flatMap(this::normalizeBlank);
    }

    private Optional<Element> firstTag(Element element, String tag) {
        return Optional.ofNullable(element.selectFirst(tag));
    }

    private List<Element> tagsWithClassContaining(Document document, String tag, String classSignal) {
        String normalizedSignal = classSignal.toLowerCase(Locale.ROOT);
        Elements elements = document.select(tag + "[class]");
        return elements.stream()
                .filter(element -> element.className().toLowerCase(Locale.ROOT).contains(normalizedSignal))
                .toList();
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

    private String elementToText(Element element) {
        StringBuilder builder = new StringBuilder();
        appendText(element, builder);
        return normalizeText(builder.toString());
    }

    private void appendText(Node node, StringBuilder builder) {
        if (node instanceof TextNode textNode) {
            builder.append(textNode.text()).append(' ');
            return;
        }

        if (node instanceof Element element && element.tagName().equalsIgnoreCase("br")) {
            builder.append('\n');
            return;
        }

        boolean block = node instanceof Element element && BLOCK_TAGS.contains(element.tagName().toLowerCase(Locale.ROOT));
        if (block) {
            builder.append('\n');
        }
        for (Node child : node.childNodes()) {
            appendText(child, builder);
        }
        if (block) {
            builder.append('\n');
        }
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

    public boolean looksLikeArticle(String html) {
        Document document = Jsoup.parse(html == null ? "" : html);
        if (document.selectFirst("article") != null) {
            return true;
        }
        if (metaContent(document, "property", "article:published_time").isPresent()) {
            return true;
        }
        if (document.select("meta").stream()
                .anyMatch(meta -> "og:type".equalsIgnoreCase(meta.attr("property"))
                        && "article".equalsIgnoreCase(meta.attr("content")))) {
            return true;
        }
        for (Element element : document.getAllElements()) {
            for (Attribute attribute : element.attributes()) {
                if (attribute.getKey().equalsIgnoreCase("itemprop")
                        && attribute.getValue().equalsIgnoreCase("datePublished")) {
                    return true;
                }
            }
        }
        return false;
    }
}
