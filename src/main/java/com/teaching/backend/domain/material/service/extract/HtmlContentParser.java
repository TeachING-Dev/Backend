package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.MaterialImageCandidate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class HtmlContentParser {

    private static final int MAX_IMAGE_CANDIDATES = 20;
    private static final int MAX_IMAGE_CONTEXT_LENGTH = 240;
    private static final List<String> CONTENT_TAGS = List.of("article", "main", "section", "div");
    private static final List<String> IMAGE_URL_ATTRIBUTES = List.of(
            "src",
            "data-src",
            "data-original",
            "data-lazy-src"
    );
    private static final List<String> NON_CONTENT_IMAGE_SIGNALS = List.of(
            "logo",
            "favicon",
            "icon",
            "avatar",
            "profile",
            "tracking",
            "pixel",
            "advertisement",
            "banner",
            "sponsor"
    );
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
        List<MaterialImageCandidate> imageCandidates = extractImageCandidates(contentElement);

        return new ParsedHtmlContent(
                originalUrl,
                title,
                content,
                thumbnailUrl,
                author,
                publishedAt,
                imageCandidates
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
        return NOISE_SIGNALS.stream().anyMatch(signal -> hasNoiseSignal(value, signal));
    }

    private boolean hasNoiseSignal(String value, String signal) {
        if (signal.endsWith("-") || signal.endsWith("_")) {
            return value.contains(signal);
        }
        if (signal.equals("advertisement")
                || signal.equals("banner")
                || signal.equals("sponsor")
                || signal.equals("sidebar")
                || signal.equals("recommend")
                || signal.equals("related")
                || signal.equals("pagination")) {
            return value.contains(signal);
        }

        String[] tokens = value.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.equals(signal) || token.equals(signal + "s")) {
                return true;
            }
        }
        return false;
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

    private List<MaterialImageCandidate> extractImageCandidates(Element contentElement) {
        List<MaterialImageCandidate> candidates = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        for (Element image : contentElement.select("img")) {
            Optional<String> url = imageUrl(image);
            if (url.isEmpty() || !seenUrls.add(url.get())) {
                continue;
            }
            if (isNonContentImage(image, url.get())) {
                continue;
            }

            candidates.add(new MaterialImageCandidate(
                    url.get(),
                    normalizeBlank(image.attr("alt")).orElse(null),
                    captionText(image).orElse(null),
                    normalizeBlank(image.attr("title")).orElse(null),
                    sectionHeading(image, contentElement).orElse(null),
                    imageContext(image).orElse(null)
            ));
            if (candidates.size() >= MAX_IMAGE_CANDIDATES) {
                break;
            }
        }

        return List.copyOf(candidates);
    }

    private Optional<String> imageUrl(Element image) {
        for (String attribute : IMAGE_URL_ATTRIBUTES) {
            Optional<String> url = normalizeImageUrl(image, image.attr(attribute));
            if (url.isPresent()) {
                return url;
            }
        }
        return srcsetUrl(image);
    }

    private Optional<String> srcsetUrl(Element image) {
        String srcset = image.attr("srcset");
        if (srcset == null || srcset.isBlank()) {
            return Optional.empty();
        }

        for (String candidate : srcset.split(",")) {
            String rawUrl = candidate.trim().split("\\s+")[0];
            Optional<String> url = normalizeImageUrl(image, rawUrl);
            if (url.isPresent()) {
                return url;
            }
        }
        return Optional.empty();
    }

    private Optional<String> normalizeImageUrl(Element image, String rawUrl) {
        return normalizeBlank(rawUrl)
                .filter(value -> !isPlaceholderImageUrl(value))
                .flatMap(value -> {
                    try {
                        return normalizeBlank(URI.create(image.baseUri()).resolve(value).toString());
                    } catch (RuntimeException e) {
                        return Optional.empty();
                    }
                })
                .filter(this::isAllowedImageUrl);
    }

    private boolean isAllowedImageUrl(String url) {
        String normalized = url.toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private boolean isPlaceholderImageUrl(String url) {
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank()
                || normalized.startsWith("data:")
                || normalized.startsWith("blob:")
                || normalized.startsWith("javascript:")
                || normalized.equals("about:blank")
                || normalized.contains("placeholder")
                || normalized.contains("spacer")
                || normalized.contains("blank.gif")
                || normalized.contains("1x1")
                || normalized.contains("transparent");
    }

    private boolean isNonContentImage(Element image, String url) {
        if (isTinyImage(image)) {
            return true;
        }

        String signalSource = String.join(" ",
                url,
                image.attr("alt"),
                image.attr("title"),
                image.className(),
                image.id()
        ).toLowerCase(Locale.ROOT);

        return NON_CONTENT_IMAGE_SIGNALS.stream().anyMatch(signalSource::contains);
    }

    private boolean isTinyImage(Element image) {
        Optional<Integer> width = positiveInteger(image.attr("width"));
        Optional<Integer> height = positiveInteger(image.attr("height"));
        return width.isPresent() && height.isPresent() && width.get() <= 2 && height.get() <= 2;
    }

    private Optional<Integer> positiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<String> captionText(Element image) {
        Element figure = image.closest("figure");
        if (figure == null) {
            return Optional.empty();
        }
        Element caption = figure.selectFirst("figcaption");
        return caption == null ? Optional.empty() : normalizeBlank(elementToText(caption));
    }

    private Optional<String> sectionHeading(Element image, Element contentElement) {
        Element current = image;
        while (current != null && current != contentElement) {
            for (Element sibling = current.previousElementSibling(); sibling != null; sibling = sibling.previousElementSibling()) {
                Optional<String> heading = headingText(sibling);
                if (heading.isPresent()) {
                    return heading;
                }
            }
            current = current.parent();
        }
        return Optional.empty();
    }

    private Optional<String> headingText(Element element) {
        if (isHeading(element)) {
            return normalizeBlank(elementToText(element));
        }
        Element heading = element.selectFirst("h1, h2, h3, h4, h5, h6");
        return heading == null ? Optional.empty() : normalizeBlank(elementToText(heading));
    }

    private boolean isHeading(Element element) {
        return element != null && element.tagName().matches("(?i)h[1-6]");
    }

    private Optional<String> imageContext(Element image) {
        List<String> parts = new ArrayList<>();
        nearbyText(image, true).ifPresent(parts::add);
        nearbyText(image, false).ifPresent(parts::add);

        if (parts.isEmpty() && image.parent() != null) {
            normalizeBlank(elementToText(image.parent())).ifPresent(parts::add);
        }

        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return normalizeBlank(truncate(String.join(" ", parts), MAX_IMAGE_CONTEXT_LENGTH));
    }

    private Optional<String> nearbyText(Element image, boolean previous) {
        for (Element sibling = previous ? image.previousElementSibling() : image.nextElementSibling();
             sibling != null;
             sibling = previous ? sibling.previousElementSibling() : sibling.nextElementSibling()) {
            if (sibling.tagName().equalsIgnoreCase("img")) {
                continue;
            }
            Optional<String> text = normalizeBlank(elementToText(sibling));
            if (text.isPresent()) {
                return text;
            }
        }
        return Optional.empty();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
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
