package com.teaching.backend.domain.material.service.extract;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.List;
import java.util.Locale;

final class ProtectedSourceDetector {

    private static final List<String> AUTH_HOSTS = List.of(
            "accounts.google.com",
            "github.com",
            "login.microsoftonline.com",
            "login.live.com",
            "appleid.apple.com"
    );
    private static final List<String> AUTH_TEXT_SIGNALS = List.of(
            "login",
            "log in",
            "sign in",
            "signin",
            "sign up",
            "signup",
            "register",
            "로그인",
            "회원가입"
    );
    private static final List<String> ACCESS_DENIED_SIGNALS = List.of(
            "access denied",
            "unauthorized",
            "you do not have access",
            "this page is private",
            "권한이 없습니다"
    );

    private ProtectedSourceDetector() {
    }

    static boolean isAuthenticationUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = normalize(uri.getHost());
            String path = normalize(uri.getPath());
            if (host.isBlank()) {
                return false;
            }
            if (isKnownAuthHost(host)) {
                return true;
            }
            return hasAuthPathSegment(path);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean isProtectedHtml(String html, String currentUrl) {
        return isProtectedHtml(html, currentUrl, currentUrl);
    }

    static boolean isProtectedHtml(String html, String currentUrl, String originalUrl) {
        if (html == null || html.isBlank()) {
            return false;
        }
        if (isAuthenticationUrl(currentUrl)) {
            return true;
        }

        Document document = Jsoup.parse(html, currentUrl == null ? "" : currentUrl);
        if (hasNotionPrivateSignal(document)) {
            return true;
        }
        if (isNotionAppPrivateShell(document, originalUrl, currentUrl)) {
            return true;
        }
        if (hasAccessDeniedHeading(document)) {
            return true;
        }

        boolean hasPasswordInput = !document.select("input[type=password]").isEmpty();
        if (!hasPasswordInput) {
            return false;
        }

        return hasAuthTitleOrHeading(document) || hasAuthFormSignal(document);
    }

    static boolean hasNotionPrivateSignal(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        return hasNotionPrivateSignal(Jsoup.parse(html));
    }

    private static boolean isKnownAuthHost(String host) {
        return AUTH_HOSTS.stream().anyMatch(authHost -> host.equals(authHost) || host.endsWith("." + authHost));
    }

    private static boolean hasAuthPathSegment(String path) {
        if (path.isBlank()) {
            return false;
        }
        String[] segments = path.split("/+");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isBlank()) {
                continue;
            }
            if (segment.equals("login") || segment.equals("signin") || segment.equals("sign-in")) {
                return true;
            }
            if ((segment.equals("auth") || segment.equals("oauth") || segment.equals("sso")) && isFirstPathSegment(segments, i)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFirstPathSegment(String[] segments, int index) {
        for (int i = 0; i < index; i++) {
            if (!segments[i].isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAuthTitleOrHeading(Document document) {
        String title = normalizeText(document.title());
        if (hasAuthTextSignal(title)) {
            return true;
        }
        for (Element heading : document.select("h1, h2, h3, [role=heading]")) {
            if (hasAuthTextSignal(normalizeText(heading.text()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAuthFormSignal(Document document) {
        for (Element form : document.select("form")) {
            String signalSource = normalizeText(String.join(" ",
                    form.attr("action"),
                    form.id(),
                    form.className(),
                    form.attr("aria-label"),
                    form.attr("name")
            ));
            if (hasAuthTextSignal(signalSource) || hasAuthPathSegment(signalSource.replace(' ', '/'))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNotionPrivateSignal(Document document) {
        String text = normalizeText(document.text());
        return text.contains("this page is private") || text.contains("you do not have access");
    }

    private static boolean isNotionAppPrivateShell(Document document, String originalUrl, String currentUrl) {
        if (!isNotionAppDocumentUrl(originalUrl) && !isNotionAppDocumentUrl(currentUrl)) {
            return false;
        }
        if (hasNotionDocumentContentSignal(document)) {
            return false;
        }

        String title = normalizeText(document.title());
        String text = normalizeText(document.text());
        boolean marketingTitle = title.contains("notion")
                && title.contains("where teams and agents work together");
        boolean shellAuthSignal = hasNotionShellAuthSignal(document, text);

        return marketingTitle && shellAuthSignal;
    }

    private static boolean isNotionAppDocumentUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = normalize(uri.getHost());
            if (!host.equals("app.notion.com")) {
                return false;
            }
            String[] segments = normalize(uri.getPath()).split("/+");
            for (String segment : segments) {
                if (!segment.isBlank()) {
                    return segment.equals("p");
                }
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean hasNotionDocumentContentSignal(Document document) {
        return document.selectFirst(".notion-page-content, .notion-page-block, [data-block-id], [data-content-editable-leaf]") != null;
    }

    private static boolean hasNotionShellAuthSignal(Document document, String text) {
        if (text.contains("log in") || text.contains("sign up") || text.contains("get notion free")) {
            return true;
        }
        for (Element element : document.select("a[href], button, [role=button]")) {
            String signalSource = normalizeText(String.join(" ",
                    element.attr("href"),
                    element.attr("aria-label"),
                    element.text()
            ));
            if (signalSource.contains("log in")
                    || signalSource.contains("login")
                    || signalSource.contains("sign in")
                    || signalSource.contains("sign up")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAccessDeniedHeading(Document document) {
        for (Element heading : document.select("title, h1, h2, h3, [role=heading]")) {
            String text = normalizeText(heading.text());
            if (ACCESS_DENIED_SIGNALS.stream().anyMatch(text::equals)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAuthTextSignal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return AUTH_TEXT_SIGNALS.stream().anyMatch(value::contains);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        return normalize(value).replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
    }
}
