package com.teaching.backend.domain.material.service.extract;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.net.URI;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RenderedHtmlDocumentClient {

    private static final int NOTION_SCROLL_STEPS = 8;
    private static final long NOTION_SCROLL_WAIT_MS = 250L;

    private final boolean enabled;
    private final Duration pageLoadTimeout;
    private final Duration scriptTimeout;
    private final Duration waitTimeout;
    private final int minVisibleTextLength;
    private final Duration acquireTimeout;
    private final Semaphore renderPermits;
    private final ExternalHtmlDocumentClient urlValidator;

    @Autowired
    public RenderedHtmlDocumentClient(
            ExternalHtmlDocumentClient urlValidator,
            @Value("${material.extract.rendered.enabled:true}") boolean enabled,
            @Value("${material.extract.rendered.page-load-timeout-ms:10000}") long pageLoadTimeoutMs,
            @Value("${material.extract.rendered.script-timeout-ms:5000}") long scriptTimeoutMs,
            @Value("${material.extract.rendered.wait-timeout-ms:7000}") long waitTimeoutMs,
            @Value("${material.extract.rendered.min-visible-text-length:20}") int minVisibleTextLength,
            @Value("${material.extract.rendered.max-concurrency:2}") int maxConcurrency,
            @Value("${material.extract.rendered.acquire-timeout-ms:500}") long acquireTimeoutMs
    ) {
        this(
                enabled,
                pageLoadTimeoutMs,
                scriptTimeoutMs,
                waitTimeoutMs,
                minVisibleTextLength,
                maxConcurrency,
                acquireTimeoutMs,
                urlValidator
        );
    }

    RenderedHtmlDocumentClient(
            boolean enabled,
            long pageLoadTimeoutMs,
            long scriptTimeoutMs,
            long waitTimeoutMs,
            int minVisibleTextLength
    ) {
        this(enabled, pageLoadTimeoutMs, scriptTimeoutMs, waitTimeoutMs, minVisibleTextLength, 2, 500, null);
    }

    RenderedHtmlDocumentClient(
            boolean enabled,
            long pageLoadTimeoutMs,
            long scriptTimeoutMs,
            long waitTimeoutMs,
            int minVisibleTextLength,
            int maxConcurrency,
            long acquireTimeoutMs,
            ExternalHtmlDocumentClient urlValidator
    ) {
        this(
                enabled,
                pageLoadTimeoutMs,
                scriptTimeoutMs,
                waitTimeoutMs,
                minVisibleTextLength,
                acquireTimeoutMs,
                urlValidator,
                new Semaphore(Math.max(1, maxConcurrency))
        );
    }

    RenderedHtmlDocumentClient(
            boolean enabled,
            long pageLoadTimeoutMs,
            long scriptTimeoutMs,
            long waitTimeoutMs,
            int minVisibleTextLength,
            long acquireTimeoutMs,
            ExternalHtmlDocumentClient urlValidator,
            Semaphore renderPermits
    ) {
        this.enabled = enabled;
        this.pageLoadTimeout = Duration.ofMillis(pageLoadTimeoutMs);
        this.scriptTimeout = Duration.ofMillis(scriptTimeoutMs);
        this.waitTimeout = Duration.ofMillis(waitTimeoutMs);
        this.minVisibleTextLength = minVisibleTextLength;
        this.acquireTimeout = Duration.ofMillis(Math.max(0L, acquireTimeoutMs));
        this.renderPermits = renderPermits;
        this.urlValidator = urlValidator;
    }

    public Optional<HtmlDocument> render(String originalUrl) {
        String safeUrl = safeUrl(originalUrl);
        if (!enabled) {
            log.debug("Rendered HTML fallback is disabled. url={}", safeUrl);
            return Optional.empty();
        }
        if (!isAllowedUrl(originalUrl, "initial")) {
            return Optional.empty();
        }

        WebDriver driver = null;
        boolean permitAcquired = false;
        try {
            permitAcquired = acquirePermit(safeUrl);
            if (!permitAcquired) {
                return Optional.empty();
            }
            log.info("Rendered HTML fallback started. url={}", safeUrl);
            driver = createDriver();
            log.debug("Rendered HTML driver created. url={}", safeUrl);
            driver.manage().timeouts().pageLoadTimeout(pageLoadTimeout);
            driver.manage().timeouts().scriptTimeout(scriptTimeout);
            driver.get(originalUrl);
            String currentUrl = driver.getCurrentUrl();
            if (currentUrl == null || currentUrl.isBlank() || !isAllowedUrl(currentUrl, "final")) {
                return Optional.empty();
            }
            log.debug(
                    "Rendered HTML page loaded. url={}, readyState={}, visibleTextLength={}",
                    safeUrl,
                    readyState(driver),
                    visibleTextLength(driver)
            );
            waitUntilReadable(driver);
            currentUrl = driver.getCurrentUrl();
            if (currentUrl == null || currentUrl.isBlank() || !isAllowedUrl(currentUrl, "final")) {
                return Optional.empty();
            }
            triggerNotionLazyLoad(driver, currentUrl);

            String pageSource = driver.getPageSource();
            log.info(
                    "Rendered HTML page source captured. url={}, pageSourceLength={}, visibleTextLength={}",
                    safeUrl,
                    pageSource == null ? 0 : pageSource.length(),
                    visibleTextLength(driver)
            );
            if (pageSource == null || pageSource.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new HtmlDocument(originalUrl, pageSource, "text/html"));
        } catch (RuntimeException e) {
            log.warn(
                    "Rendered HTML fallback failed. url={}, reason={}, message={}",
                    safeUrl,
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
            return Optional.empty();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (RuntimeException e) {
                    log.warn(
                            "Rendered HTML driver cleanup failed. url={}, reason={}, message={}",
                            safeUrl,
                            e.getClass().getSimpleName(),
                            e.getMessage()
                    );
                }
            }
            if (permitAcquired) {
                renderPermits.release();
            }
        }
    }

    protected WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        options.addArguments(
                "--headless=new",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=1365,1800"
        );
        return new ChromeDriver(options);
    }

    private boolean acquirePermit(String safeUrl) {
        try {
            boolean acquired = acquireTimeout.isZero()
                    ? renderPermits.tryAcquire()
                    : renderPermits.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.info("Rendered HTML fallback skipped by concurrency limit. url={}", safeUrl);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Rendered HTML fallback interrupted while waiting for concurrency permit. url={}", safeUrl);
            return false;
        }
    }

    private boolean isAllowedUrl(String url, String stage) {
        if (urlValidator == null) {
            return true;
        }
        try {
            urlValidator.validateFetchTarget(url);
            return true;
        } catch (RuntimeException e) {
            log.warn(
                    "Rendered HTML fallback rejected URL. stage={}, url={}, reason={}",
                    stage,
                    safeUrl(url),
                    e.getClass().getSimpleName()
            );
            return false;
        }
    }

    private void waitUntilReadable(WebDriver driver) {
        try {
            new WebDriverWait(driver, waitTimeout).until(webDriver ->
                    isDocumentComplete(webDriver) && visibleTextLength(webDriver) >= minVisibleTextLength
            );
        } catch (TimeoutException e) {
            log.debug("Rendered HTML wait timed out. currentVisibleTextLength={}", visibleTextLength(driver));
        }
    }

    private boolean isDocumentComplete(WebDriver driver) {
        return "complete".equals(readyState(driver));
    }

    private void triggerNotionLazyLoad(WebDriver driver, String currentUrl) {
        if (!isNotionUrl(currentUrl) || !(driver instanceof JavascriptExecutor javascriptExecutor)) {
            return;
        }

        try {
            Object expandedCount = javascriptExecutor.executeScript(
                    """
                            let expandedCount = 0;
                            document.querySelectorAll('.notion-toggle-block').forEach(element => {
                              try {
                                element.scrollIntoView({block: 'center'});
                                const target = element.querySelector('[role="button"], button') || element;
                                target.click();
                                expandedCount++;
                              } catch (error) {
                              }
                            });
                            return expandedCount;
                            """
            );
            log.debug(
                    "Rendered HTML Notion toggle expansion completed. url={}, expandedCount={}",
                    safeUrl(currentUrl),
                    expandedCount
            );
            for (int step = 0; step < NOTION_SCROLL_STEPS; step++) {
                javascriptExecutor.executeScript(
                        "window.scrollTo(0, Math.min(document.body.scrollHeight, window.innerHeight * arguments[0]));",
                        step + 1
                );
                sleepAfterScroll();
            }
            javascriptExecutor.executeScript("window.scrollTo(0, 0);");
            log.debug("Rendered HTML Notion lazy-load scroll completed. url={}", safeUrl(currentUrl));
        } catch (RuntimeException e) {
            log.debug(
                    "Rendered HTML Notion lazy-load scroll failed. url={}, reason={}",
                    safeUrl(currentUrl),
                    e.getClass().getSimpleName()
            );
        }
    }

    private boolean isNotionUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String normalized = host.toLowerCase();
            return normalized.equals("notion.so")
                    || normalized.endsWith(".notion.so")
                    || normalized.equals("notion.site")
                    || normalized.endsWith(".notion.site");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void sleepAfterScroll() {
        try {
            Thread.sleep(NOTION_SCROLL_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Object readyState(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor javascriptExecutor)) {
            return "unknown";
        }
        return javascriptExecutor.executeScript("return document.readyState");
    }

    private int visibleTextLength(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor javascriptExecutor)) {
            String source = driver.getPageSource();
            return source == null ? 0 : source.length();
        }
        Object value = javascriptExecutor.executeScript(
                "return document.body && document.body.innerText ? document.body.innerText.trim().length : 0"
        );
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String safeUrl(String originalUrl) {
        try {
            URI uri = URI.create(originalUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            return scheme + "://" + host + path;
        } catch (RuntimeException e) {
            return "[invalid-url]";
        }
    }
}
