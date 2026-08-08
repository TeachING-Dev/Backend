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
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;

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
            "button",
            "btn_",
            "quickeditor",
            "toolbar",
            "emoticon",
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
        return parse(originalUrl, html, contentClassSignals, List.of());
    }

    ParsedHtmlContent parse(
            String originalUrl,
            String html,
            List<String> contentClassSignals,
            List<String> preservedContentSelectors
    ) {
        Document document = Jsoup.parse(html == null ? "" : html, originalUrl);
        removeNoise(document, preservedContentSelectors);
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

    private void removeNoise(Document document, List<String> preservedContentSelectors) {
        document.select("script, style, noscript, nav, footer, aside").remove();
        document.select("[class], [id]").stream()
                .filter(element -> hasNoiseSignal(element) && !containsPreservedContent(element, preservedContentSelectors))
                .forEach(Element::remove);
    }

    private boolean containsPreservedContent(Element element, List<String> preservedContentSelectors) {
        if (preservedContentSelectors == null || preservedContentSelectors.isEmpty()) {
            return false;
        }
        for (String selector : preservedContentSelectors) {
            if (element.is(selector) || element.selectFirst(selector) != null) {
                return true;
            }
        }
        return false;
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
        List<RankedImageCandidate> candidates = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        int index = 0;

        for (Element image : contentElement.select("img")) {
            Optional<String> url = imageUrl(image);
            if (url.isEmpty() || !seenUrls.add(url.get())) {
                index++;
                continue;
            }
            if (isNonContentImage(image, url.get())) {
                index++;
                continue;
            }

            MaterialImageCandidate candidate = new MaterialImageCandidate(
                    url.get(),
                    normalizeBlank(image.attr("alt")).orElse(null),
                    captionText(image).orElse(null),
                    normalizeBlank(image.attr("title")).orElse(null),
                    sectionHeading(image, contentElement).orElse(null),
                    imageContext(image, contentElement).orElse(null)
            );
            candidates.add(new RankedImageCandidate(candidate, index, imageCandidateScore(candidate)));
            index++;
        }

        return selectImageCandidates(candidates);
    }

    private List<MaterialImageCandidate> selectImageCandidates(List<RankedImageCandidate> candidates) {
        if (candidates.size() <= MAX_IMAGE_CANDIDATES) {
            return candidates.stream()
                    .map(RankedImageCandidate::candidate)
                    .toList();
        }

        List<RankedImageCandidate> selected = new ArrayList<>();
        List<RankedImageCandidate> ranked = candidates.stream()
                .sorted(Comparator
                        .comparingInt(RankedImageCandidate::score).reversed()
                        .thenComparingInt(RankedImageCandidate::index))
                .toList();

        int qualityQuota = Math.min(MAX_IMAGE_CANDIDATES / 2, ranked.size());
        for (int i = 0; i < qualityQuota; i++) {
            selected.add(ranked.get(i));
        }

        int remainingSlots = MAX_IMAGE_CANDIDATES - selected.size();
        if (remainingSlots > 0) {
            for (int slot = 0; slot < remainingSlots; slot++) {
                int targetIndex = evenlySpacedTargetIndex(slot, remainingSlots, candidates.size());
                nearestUnselectedCandidate(candidates, selected, targetIndex).ifPresent(selected::add);
            }
        }

        if (selected.size() < MAX_IMAGE_CANDIDATES) {
            ranked.stream()
                    .filter(candidate -> !selected.contains(candidate))
                    .limit(MAX_IMAGE_CANDIDATES - selected.size())
                    .forEach(selected::add);
        }

        return selected.stream()
                .sorted(Comparator.comparingInt(RankedImageCandidate::index))
                .map(RankedImageCandidate::candidate)
                .toList();
    }

    private int evenlySpacedTargetIndex(int slot, int slots, int totalCandidates) {
        if (slots <= 1) {
            return totalCandidates / 2;
        }
        return (int) Math.round((double) slot * (totalCandidates - 1) / (slots - 1));
    }

    private Optional<RankedImageCandidate> nearestUnselectedCandidate(
            List<RankedImageCandidate> candidates,
            List<RankedImageCandidate> selected,
            int targetIndex
    ) {
        return candidates.stream()
                .filter(candidate -> !selected.contains(candidate))
                .min(Comparator
                        .comparingInt((RankedImageCandidate candidate) -> Math.abs(candidate.index() - targetIndex))
                        .thenComparing(Comparator.comparingInt(RankedImageCandidate::score).reversed())
                        .thenComparingInt(RankedImageCandidate::index));
    }

    private int imageCandidateScore(MaterialImageCandidate candidate) {
        int score = 0;
        score += metadataScore(candidate.caption(), 4);
        score += metadataScore(candidate.context(), 3);
        score += metadataScore(candidate.sectionHeading(), 2);
        score += metadataScore(candidate.alt(), 1);
        score += metadataScore(candidate.title(), 1);
        return score;
    }

    private int metadataScore(String value, int weight) {
        return isMeaningfulMetadata(value) ? weight : 0;
    }

    private boolean isMeaningfulMetadata(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3) {
            return false;
        }
        if (NON_CONTENT_IMAGE_SIGNALS.stream().anyMatch(signal -> hasTokenSignal(normalized, signal))) {
            return false;
        }
        return !normalized.matches("(?i).+\\.(png|jpe?g|gif|webp|svg)$");
    }

    private Optional<String> imageUrl(Element image) {
        for (String attribute : IMAGE_URL_ATTRIBUTES) {
            Optional<String> url = normalizeImageUrl(image, attribute, image.attr(attribute));
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
            Optional<String> url = normalizeImageUrl(image, null, rawUrl);
            if (url.isPresent()) {
                return url;
            }
        }
        return Optional.empty();
    }

    private Optional<String> normalizeImageUrl(Element image, String attributeName, String rawUrl) {
        return normalizeBlank(rawUrl)
                .filter(value -> !isPlaceholderImageUrl(value))
                .flatMap(value -> {
                    if (attributeName != null && !attributeName.isBlank()) {
                        Optional<String> absoluteUrl = normalizeBlank(image.absUrl(attributeName))
                                .filter(url -> !isPlaceholderImageUrl(url));
                        if (absoluteUrl.isPresent()) {
                            return absoluteUrl;
                        }
                    }
                    try {
                        return normalizeBlank(new URI(image.baseUri())
                                .resolve(sanitizeUrlWhitespace(value))
                                .toString());
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                })
                .filter(this::isAllowedImageUrl);
    }

    private String sanitizeUrlWhitespace(String value) {
        return value.trim().replaceAll("\\s", "%20");
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
                || normalized.contains("w80_blur")
                || normalized.contains("1x1")
                || normalized.contains("transparent");
    }

    private boolean isNonContentImage(Element image, String url) {
        if (isTinyImage(image)) {
            return true;
        }

        return hasNonContentUrlSignal(url)
                || hasNonContentAttributeSignal(image.attr("alt"))
                || hasNonContentAttributeSignal(image.attr("title"))
                || hasNonContentAttributeSignal(image.className())
                || hasNonContentAttributeSignal(image.id());
    }

    private boolean hasNonContentAttributeSignal(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return NON_CONTENT_IMAGE_SIGNALS.stream().anyMatch(signal -> hasTokenSignal(normalized, signal));
    }

    private boolean hasNonContentUrlSignal(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            String[] segments = path.split("/");
            for (int index = 0; index < segments.length; index++) {
                String segment = segments[index];
                if (segment.isBlank()) {
                    continue;
                }
                boolean filename = index == segments.length - 1;
                String candidate = filename ? stripFileExtension(segment) : segment;
                if (hasNonContentAttributeSignal(candidate)) {
                    return true;
                }
            }
            return false;
        } catch (URISyntaxException e) {
            return hasNonContentAttributeSignal(url);
        }
    }

    private String stripFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex <= 0 ? filename : filename.substring(0, dotIndex);
    }

    private boolean hasTokenSignal(String value, String signal) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (signal.endsWith("_") || signal.endsWith("-")) {
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

    private Optional<String> imageContext(Element image, Element contentElement) {
        List<String> parts = new ArrayList<>();
        addNearbyText(parts, image, contentElement);

        Element current = image.parent();
        int ancestorDepth = 0;
        while (parts.isEmpty()
                && current != null
                && current != contentElement
                && ancestorDepth < 2) {
            addNearbyText(parts, current, contentElement);
            current = current.parent();
            ancestorDepth++;
        }

        if (parts.isEmpty() && image.parent() != null && isInsideContentElement(image.parent(), contentElement)) {
            contextText(image.parent()).ifPresent(parts::add);
        }

        if (parts.isEmpty()) {
            return Optional.empty();
        }

        List<String> distinctParts = new ArrayList<>(new LinkedHashSet<>(parts));
        return normalizeBlank(truncate(String.join(" ", distinctParts), MAX_IMAGE_CONTEXT_LENGTH));
    }

    private void addNearbyText(List<String> parts, Element element, Element contentElement) {
        nearbyText(element, true, contentElement).ifPresent(parts::add);
        nearbyText(element, false, contentElement).ifPresent(parts::add);
    }

    private Optional<String> nearbyText(Element element, boolean previous, Element contentElement) {
        for (Element sibling = previous ? element.previousElementSibling() : element.nextElementSibling();
             sibling != null;
             sibling = previous ? sibling.previousElementSibling() : sibling.nextElementSibling()) {
            if (!isInsideContentElement(sibling, contentElement)) {
                break;
            }
            if (sibling.tagName().equalsIgnoreCase("img") || hasNoiseSignal(sibling)) {
                continue;
            }
            Optional<String> text = contextText(sibling);
            if (text.isPresent()) {
                return text;
            }
        }
        return Optional.empty();
    }

    private Optional<String> contextText(Element element) {
        return normalizeBlank(elementToText(element))
                .filter(this::isMeaningfulContextText);
    }

    private boolean isMeaningfulContextText(String text) {
        return text != null
                && text.trim().length() >= 8
                && !isUiOnlyLine(text.trim());
    }

    private boolean isInsideContentElement(Element element, Element contentElement) {
        return element != null
                && contentElement != null
                && (element == contentElement || element.parents().contains(contentElement));
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

    private record RankedImageCandidate(MaterialImageCandidate candidate, int index, int score) {
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
