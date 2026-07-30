package com.teaching.backend.domain.material.service.extract;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.net.URI;

@Component
@Slf4j
public class RenderedHtmlDocumentClient {

    private final boolean enabled;
    private final Duration pageLoadTimeout;
    private final Duration scriptTimeout;
    private final Duration waitTimeout;
    private final int minVisibleTextLength;

    public RenderedHtmlDocumentClient(
            @Value("${material.extract.rendered.enabled:true}") boolean enabled,
            @Value("${material.extract.rendered.page-load-timeout-ms:10000}") long pageLoadTimeoutMs,
            @Value("${material.extract.rendered.script-timeout-ms:5000}") long scriptTimeoutMs,
            @Value("${material.extract.rendered.wait-timeout-ms:7000}") long waitTimeoutMs,
            @Value("${material.extract.rendered.min-visible-text-length:20}") int minVisibleTextLength
    ) {
        this.enabled = enabled;
        this.pageLoadTimeout = Duration.ofMillis(pageLoadTimeoutMs);
        this.scriptTimeout = Duration.ofMillis(scriptTimeoutMs);
        this.waitTimeout = Duration.ofMillis(waitTimeoutMs);
        this.minVisibleTextLength = minVisibleTextLength;
    }

    public Optional<HtmlDocument> render(String originalUrl) {
        String safeUrl = safeUrl(originalUrl);
        if (!enabled) {
            log.debug("Rendered HTML fallback is disabled. url={}", safeUrl);
            return Optional.empty();
        }

        WebDriver driver = null;
        try {
            log.info("Rendered HTML fallback started. url={}", safeUrl);
            driver = createDriver();
            log.debug("Rendered HTML driver created. url={}", safeUrl);
            driver.manage().timeouts().pageLoadTimeout(pageLoadTimeout);
            driver.manage().timeouts().scriptTimeout(scriptTimeout);
            driver.get(originalUrl);
            log.debug(
                    "Rendered HTML page loaded. url={}, readyState={}, visibleTextLength={}",
                    safeUrl,
                    readyState(driver),
                    visibleTextLength(driver)
            );
            waitUntilReadable(driver);

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
        }
    }

    private WebDriver createDriver() {
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
