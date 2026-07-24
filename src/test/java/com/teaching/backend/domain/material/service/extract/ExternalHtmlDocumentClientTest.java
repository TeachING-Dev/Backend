package com.teaching.backend.domain.material.service.extract;

import com.sun.net.httpserver.HttpServer;
import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalHtmlDocumentClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesNormalHtml() throws IOException {
        String url = startServer(200, "text/html", "<html><body>content</body></html>", 0);
        ExternalHtmlDocumentClient client = testClient(Duration.ofSeconds(2));

        HtmlDocument result = client.fetch(url);

        assertThat(result.body()).contains("content");
        assertThat(result.contentType()).contains("text/html");
    }

    @Test
    void acceptsHtmlContentTypeWithCharset() throws IOException {
        String url = startServer(200, "text/html; charset=utf-8", "<html><body>content</body></html>", 0);
        ExternalHtmlDocumentClient client = testClient(Duration.ofSeconds(2));

        HtmlDocument result = client.fetch(url);

        assertThat(result.contentType()).contains("text/html");
    }

    @Test
    void acceptsXhtmlContentType() throws IOException {
        String url = startServer(200, "application/xhtml+xml", "<html><body>content</body></html>", 0);
        ExternalHtmlDocumentClient client = testClient(Duration.ofSeconds(2));

        HtmlDocument result = client.fetch(url);

        assertThat(result.contentType()).contains("application/xhtml+xml");
    }

    @Test
    void failsWhenStatusIs404() throws IOException {
        String url = startServer(404, "text/html", "not found", 0);
        ExternalHtmlDocumentClient client = testClient(Duration.ofSeconds(2));

        assertExtractionFailed(() -> client.fetch(url));
    }

    @Test
    void failsWhenStatusIs500() throws IOException {
        String url = startServer(500, "text/html", "error", 0);
        ExternalHtmlDocumentClient client = testClient(Duration.ofSeconds(2));

        assertExtractionFailed(() -> client.fetch(url));
    }

    @Test
    void failsWhenResponseTimesOut() throws IOException {
        String url = startServer(200, "text/html", "<html></html>", 500);
        ExternalHtmlDocumentClient client = testClient(Duration.ofMillis(100));

        assertExtractionFailed(() -> client.fetch(url));
    }

    @Test
    void failsWhenResponseRedirects() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location", "/next");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        ExternalHtmlDocumentClient client = testClient(Duration.ofSeconds(2));

        assertExtractionFailed(() -> client.fetch("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    @Test
    void failsWhenContentTypeIsNotHtml() throws IOException {
        String url = startServer(200, "application/json", "{\"ok\":true}", 0);
        ExternalHtmlDocumentClient client = testClient(Duration.ofSeconds(2));

        assertExtractionFailed(() -> client.fetch(url));
    }

    @Test
    void failsWhenBodyIsEmpty() throws IOException {
        String url = startServer(200, "text/html", "", 0);
        ExternalHtmlDocumentClient client = testClient(Duration.ofSeconds(2));

        assertThatThrownBy(() -> client.fetch(url))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
    }

    @Test
    void failsWhenBodyIsLargerThanLimit() throws IOException {
        String largeBody = "<html><body>" + "a".repeat(2 * 1024 * 1024 + 1) + "</body></html>";
        String url = startServer(200, "text/html", largeBody, 0);
        ExternalHtmlDocumentClient client = testClient(Duration.ofSeconds(2));

        assertExtractionFailed(() -> client.fetch(url));
    }

    @Test
    void blocksLocalhostInProductionMode() {
        ExternalHtmlDocumentClient client = new ExternalHtmlDocumentClient(
                WebClient.builder().build(),
                Duration.ofSeconds(1),
                true
        );

        assertExtractionFailed(() -> client.fetch("http://127.0.0.1:8080"));
    }

    @Test
    void blocksHostnameResolvedToLoopbackAddress() throws Exception {
        ExternalHtmlDocumentClient client = productionLikeClient(host -> List.of(
                InetAddress.getByName("127.0.0.1")
        ));

        assertExtractionFailed(() -> client.fetch("http://public-looking.example"));
    }

    @Test
    void blocksHostnameResolvedToPrivateAddress() throws Exception {
        ExternalHtmlDocumentClient client = productionLikeClient(host -> List.of(
                InetAddress.getByName("10.0.0.3")
        ));

        assertExtractionFailed(() -> client.fetch("http://public-looking.example"));
    }

    @Test
    void blocksHostnameResolvedToLinkLocalAddress() throws Exception {
        ExternalHtmlDocumentClient client = productionLikeClient(host -> List.of(
                InetAddress.getByName("169.254.1.2")
        ));

        assertExtractionFailed(() -> client.fetch("http://public-looking.example"));
    }

    @Test
    void blocksHostnameResolvedToIpv6LoopbackAddress() throws Exception {
        ExternalHtmlDocumentClient client = productionLikeClient(host -> List.of(
                InetAddress.getByName("::1")
        ));

        assertExtractionFailed(() -> client.fetch("http://public-looking.example"));
    }

    @Test
    void blocksHostnameWhenOneResolvedAddressIsPrivate() throws Exception {
        ExternalHtmlDocumentClient client = productionLikeClient(host -> List.of(
                InetAddress.getByName("93.184.216.34"),
                InetAddress.getByName("192.168.0.2")
        ));

        assertExtractionFailed(() -> client.fetch("http://public-looking.example"));
    }

    @Test
    void blocksWhenDnsLookupFails() {
        ExternalHtmlDocumentClient client = productionLikeClient(host -> {
            throw new UnknownHostException(host);
        });

        assertExtractionFailed(() -> client.fetch("http://public-looking.example"));
    }

    @Test
    void allowsHostnameResolvedToPublicAddressDuringTargetValidation() throws Exception {
        ExternalHtmlDocumentClient client = productionLikeClient(host -> List.of(
                InetAddress.getByName("93.184.216.34")
        ));

        client.validateFetchTarget("http://public-looking.example");
    }

    private String startServer(
            int status,
            String contentType,
            String body,
            long delayMillis
    ) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", contentType);
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private ExternalHtmlDocumentClient testClient(Duration timeout) {
        return new ExternalHtmlDocumentClient(
                WebClient.builder().build(),
                timeout,
                false
        );
    }

    private ExternalHtmlDocumentClient productionLikeClient(HostAddressResolver resolver) {
        return new ExternalHtmlDocumentClient(
                WebClient.builder().build(),
                Duration.ofSeconds(1),
                true,
                resolver
        );
    }

    private void assertExtractionFailed(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
    }
}
