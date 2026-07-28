package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import io.netty.channel.ChannelOption;
import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
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
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class ExternalHtmlDocumentClient {

    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int ERROR_BODY_LOG_LIMIT = 500;

    private final WebClient webClient;
    private final HttpClient httpClient;
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
        this.httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(responseTimeout);

        this.webClient = null;
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
        this.httpClient = null;
        this.responseTimeout = responseTimeout;
        this.blockPrivateNetwork = blockPrivateNetwork;
        this.hostAddressResolver = hostAddressResolver;
    }

    ExternalHtmlDocumentClient(
            HttpClient httpClient,
            Duration responseTimeout,
            boolean blockPrivateNetwork,
            HostAddressResolver hostAddressResolver
    ) {
        this.webClient = null;
        this.httpClient = httpClient;
        this.responseTimeout = responseTimeout;
        this.blockPrivateNetwork = blockPrivateNetwork;
        this.hostAddressResolver = hostAddressResolver;
    }

    public HtmlDocument fetch(String originalUrl) {
        FetchTarget target = validateFetchTargetWithAddresses(originalUrl);
        URI uri = target.uri();
        String host = target.host();
        WebClient requestWebClient = webClientFor(target);

        try {
            HtmlDocument document = requestWebClient.get()
                    .uri(uri)
                    .exchangeToMono(response -> {
                        HttpStatusCode statusCode = response.statusCode();
                        if (!statusCode.is2xxSuccessful()) {
                            return response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> extractionFailed(
                                            "fetch",
                                            host,
                                            statusCode,
                                            body,
                                            null
                                    ));
                        }

                        MediaType contentType = response.headers().contentType().orElse(null);
                        if (!isHtml(contentType)) {
                            return response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> extractionFailed(
                                            "fetch",
                                            host,
                                            statusCode,
                                            body,
                                            null
                                    ));
                        }

                        long contentLength = response.headers().contentLength().orElse(-1L);
                        if (contentLength > MAX_RESPONSE_BYTES) {
                            return response.releaseBody()
                                    .then(extractionFailed(
                                            "fetch",
                                            host,
                                            statusCode,
                                            "contentLength=" + contentLength,
                                            null
                                    ));
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
            logExtractionFailure("fetch", host, null, null, e);
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED, e);
        }
    }

    URI validateFetchTarget(String originalUrl) {
        return validateFetchTargetWithAddresses(originalUrl).uri();
    }

    private FetchTarget validateFetchTargetWithAddresses(String originalUrl) {
        URI uri;
        try {
            uri = URI.create(originalUrl);
        } catch (IllegalArgumentException e) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED, e);
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank() || (blockPrivateNetwork && isBlockedHost(host))) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
        }

        List<InetAddress> addresses = List.of();
        if (blockPrivateNetwork || httpClient != null) {
            addresses = resolveAddresses(host);
            if (blockPrivateNetwork) {
                validateResolvedAddresses(addresses);
            }
        }

        return new FetchTarget(uri, host, addresses);
    }

    private boolean isHtml(MediaType contentType) {
        if (contentType == null) {
            return false;
        }

        return MediaType.TEXT_HTML.includes(contentType)
                || MediaType.APPLICATION_XHTML_XML.includes(contentType);
    }

    private boolean isBlockedHost(String host) {
        String normalizedHost = normalizeHost(host);
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

    private List<InetAddress> resolveAddresses(String host) {
        try {
            return List.copyOf(hostAddressResolver.resolve(host));
        } catch (UnknownHostException e) {
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED, e);
        }
    }

    private void validateResolvedAddresses(List<InetAddress> addresses) {
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

    private WebClient webClientFor(FetchTarget target) {
        if (webClient != null) {
            return webClient;
        }

        HttpClient requestHttpClient = httpClient;
        if (!target.addresses().isEmpty()) {
            requestHttpClient = requestHttpClient.resolver(
                    new PinnedHostAddressResolverGroup(target.host(), target.addresses())
            );
        }

        return WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                        .build())
                .clientConnector(new ReactorClientHttpConnector(requestHttpClient))
                .build();
    }

    private String normalizeHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
        }
        if (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        return normalizedHost;
    }

    private Mono<HtmlDocument> extractionFailed(
            String operation,
            String host,
            HttpStatusCode statusCode,
            String responseBody,
            Throwable cause
    ) {
        logExtractionFailure(operation, host, statusCode, responseBody, cause);
        if (cause == null) {
            return Mono.error(new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED));
        }
        return Mono.error(new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED, cause));
    }

    private void logExtractionFailure(
            String operation,
            String host,
            HttpStatusCode statusCode,
            String responseBody,
            Throwable exception
    ) {
        Throwable rootCause = rootCause(exception);
        log.warn(
                "Material content extraction failed. operation={}, host={}, status={}, bodyPrefix={}, exception={}, message={}, rootCause={}, rootCauseMessage={}",
                operation,
                host,
                statusCode == null ? null : statusCode.value(),
                truncate(responseBody),
                exception == null ? null : exception.getClass().getName(),
                exception == null ? null : exception.getMessage(),
                rootCause == null ? null : rootCause.getClass().getName(),
                rootCause == null ? null : rootCause.getMessage()
        );
    }

    private Throwable rootCause(Throwable exception) {
        if (exception == null) {
            return null;
        }

        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.replaceAll("[\\r\\n\\t ]+", " ").trim();
        if (normalized.length() <= ERROR_BODY_LOG_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, ERROR_BODY_LOG_LIMIT) + "...";
    }

    private record FetchTarget(
            URI uri,
            String host,
            List<InetAddress> addresses
    ) {
    }

    private static final class PinnedHostAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {

        private final String host;
        private final List<InetAddress> addresses;

        private PinnedHostAddressResolverGroup(String host, List<InetAddress> addresses) {
            this.host = host.toLowerCase(Locale.ROOT);
            this.addresses = List.copyOf(addresses);
        }

        @Override
        protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
            return new PinnedHostAddressResolver(executor, host, addresses);
        }
    }

    private static final class PinnedHostAddressResolver extends AbstractAddressResolver<InetSocketAddress> {

        private final String host;
        private final List<InetAddress> addresses;

        private PinnedHostAddressResolver(EventExecutor executor, String host, List<InetAddress> addresses) {
            super(executor, InetSocketAddress.class);
            this.host = host;
            this.addresses = addresses;
        }

        @Override
        protected boolean doIsResolved(InetSocketAddress address) {
            return address.getAddress() != null;
        }

        @Override
        protected void doResolve(InetSocketAddress unresolvedAddress, Promise<InetSocketAddress> promise) {
            if (!isExpectedHost(unresolvedAddress)) {
                promise.setFailure(new UnknownHostException(unresolvedAddress.getHostString()));
                return;
            }
            promise.setSuccess(new InetSocketAddress(addresses.get(0), unresolvedAddress.getPort()));
        }

        @Override
        protected void doResolveAll(InetSocketAddress unresolvedAddress, Promise<List<InetSocketAddress>> promise) {
            if (!isExpectedHost(unresolvedAddress)) {
                promise.setFailure(new UnknownHostException(unresolvedAddress.getHostString()));
                return;
            }

            List<InetSocketAddress> resolvedAddresses = new ArrayList<>();
            for (InetAddress address : addresses) {
                resolvedAddresses.add(new InetSocketAddress(address, unresolvedAddress.getPort()));
            }
            promise.setSuccess(resolvedAddresses);
        }

        private boolean isExpectedHost(InetSocketAddress unresolvedAddress) {
            return unresolvedAddress.getHostString().toLowerCase(Locale.ROOT).equals(host);
        }
    }
}
