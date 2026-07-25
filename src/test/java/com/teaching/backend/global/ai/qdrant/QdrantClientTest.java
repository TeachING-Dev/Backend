package com.teaching.backend.global.ai.qdrant;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.teaching.backend.global.exception.GeneralException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ensureCollectionCreatesCollectionWhenMissing() throws IOException {
        AtomicInteger getCount = new AtomicInteger();
        AtomicInteger putCount = new AtomicInteger();
        List<String> apiKeyHeaders = new ArrayList<>();
        startServer(exchange -> {
            apiKeyHeaders.add(exchange.getRequestHeaders().getFirst("api-key"));
            if ("GET".equals(exchange.getRequestMethod())) {
                getCount.incrementAndGet();
                respond(exchange, 404, "{\"status\":{\"error\":\"Not found\"}}");
                return;
            }
            if ("PUT".equals(exchange.getRequestMethod())) {
                putCount.incrementAndGet();
                respond(exchange, 200, "");
                return;
            }
            respond(exchange, 405, "");
        });

        qdrantClient(1536).ensureCollection();

        assertThat(getCount).hasValue(1);
        assertThat(putCount).hasValue(1);
        assertThat(apiKeyHeaders).allMatch("test-key"::equals);
    }

    @Test
    void upsertRejectsVectorDimensionMismatchBeforeHttpCall() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 200, "");
        });

        QdrantClient client = qdrantClient(1536);

        assertThatThrownBy(() -> client.upsertPoint(UUID.randomUUID().toString(), new float[]{0.1f}, Map.of()))
                .isInstanceOf(GeneralException.class);
        assertThat(requestCount).hasValue(0);
    }

    @Test
    void upsertErrorResponseIsConvertedToGeneralException() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, 400, "{\"status\":{\"error\":\"Bad point id\"}}");
        });

        QdrantClient client = qdrantClient(2);

        assertThatThrownBy(() -> client.upsertPoint(
                UUID.randomUUID().toString(),
                new float[]{0.1f, 0.2f},
                Map.of("materialId", 1L)
        )).isInstanceOf(GeneralException.class);
        assertThat(requestCount).hasValue(1);
    }

    private QdrantClient qdrantClient(int vectorSize) {
        return new QdrantClient(baseUrl(), "test-key", "material_chunks", vectorSize, 1000, 3000);
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/collections/material_chunks", handler::handle);
        server.createContext("/collections/material_chunks/points", handler::handle);
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
