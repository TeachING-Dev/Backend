package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Component
public class ExternalHtmlDocumentClient {

    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final WebClient webClient;
    private final Duration responseTimeout;
    private final boolean blockPrivateNetwork;
    private final HostAddressResolver hostAddressResolver;

    @Autowired
    public ExternalHtmlDocumentClient(
            HostAddressResolver hostAddressResolver,
            @Value("${material.extract.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${material.extract.response-timeout-ms:10000}") long responseTimeoutMs
    ) {
        this.hostAddressResolver = hostAddressResolver;
        this.responseTimeout = Duration.ofMillis(responseTimeoutMs);
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(responseTimeout);

        this.webClient = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                        .build())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        this.blockPrivateNetwork = true;
    }

    ExternalHtmlDocumentClient(
            WebClient webClient,
            Duration responseTimeout,
            boolean blockPrivateNetwork
    ) {
        this(webClient, responseTimeout, blockPrivateNetwork, host -> List.of());
    }

    ExternalHtmlDocumentClient(
            WebClient webClient,
            Duration responseTimeout,
            boolean blockPrivateNetwork,
            HostAddressResolver hostAddressResolver
    ) {
        this.webClient = webClient;
        this.responseTimeout = responseTimeout;
        this.blockPrivateNetwork = blockPrivateNetwork;
        this.hostAddressResolver = hostAddressResolver;
    }

    public HtmlDocument fetch(String originalUrl) {
        validateFetchTarget(originalUrl);

        try {
            HtmlDocument document = webClient.get()
                    .uri(originalUrl)
                    .exchangeToMono(response -> {
                        HttpStatusCode statusCode = response.statusCode();
                        if (!statusCode.is2xxSuccessful()) {
                            return response.releaseBody()
                                    .then(Mono.error(new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED)));
                        }

                        MediaType contentType = response.headers().contentType().orElse(null);
                        if (!isHtml(contentType)) {
                            return response.releaseBody()
                                    .then(Mono.error(new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED)));
                        }

                        long contentLength = response.headers().contentLength().orElse(-1L);
                        if (contentLength > MAX_RESPONSE_BYTES) {
                            return response.releaseBody()
                                    .then(Mono.error(new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED)));
                        }

                        return response.bodyToMono(String.class)
                                .map(body -> {
                                    if (body == null || body.isBlank()) {
                                        throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
                                    }
                                    if (body.length() > MAX_RESPONSE_BYTES) {
                                        throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
                                    }
                                    return new HtmlDocument(originalUrl, body, contentType.toString());
                                });
                    })
                    .block(responseTimeout);
            if (document == null) {
                throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EMPTY);
            }
            return document;
        } catch (MaterialException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }
    }

    void validateFetchTarget(String originalUrl) {
        URI uri;
        try {
            uri = URI.create(originalUrl);
        } catch (IllegalArgumentException e) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank() || (blockPrivateNetwork && isBlockedHost(host))) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }

        if (blockPrivateNetwork) {
            validateResolvedAddresses(host);
        }
    }

    private boolean isHtml(MediaType contentType) {
        if (contentType == null) {
            return false;
        }

        return MediaType.TEXT_HTML.includes(contentType)
                || MediaType.APPLICATION_XHTML_XML.includes(contentType);
    }

    private boolean isBlockedHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")
                || normalizedHost.equals("0.0.0.0")) {
            return true;
        }

        if (normalizedHost.equals("::1")
                || normalizedHost.startsWith("fe80:")
                || normalizedHost.startsWith("fc")
                || normalizedHost.startsWith("fd")) {
            return true;
        }

        return isPrivateIpv4(normalizedHost);
    }

    private void validateResolvedAddresses(String host) {
        List<InetAddress> addresses;
        try {
            addresses = hostAddressResolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }

        if (addresses.isEmpty() || addresses.stream().anyMatch(this::isBlockedAddress)) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }
    }

    boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    private boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        int[] numbers = new int[4];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (numbers[i] < 0 || numbers[i] > 255) {
                return false;
            }
        }

        return numbers[0] == 10
                || numbers[0] == 127
                || (numbers[0] == 172 && numbers[1] >= 16 && numbers[1] <= 31)
                || (numbers[0] == 192 && numbers[1] == 168)
                || (numbers[0] == 169 && numbers[1] == 254);
    }
}
