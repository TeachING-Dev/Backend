package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class RenderedHtmlDocumentClientTest {

    private static final String URL = "https://example.com/post";

    @Test
    void rendersWhenPermitIsAcquired() {
        WebDriver driver = readableDriver(URL);
        Semaphore permits = new Semaphore(1);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, permits, validator);

        Optional<HtmlDocument> result = client.render(URL);

        assertThat(result).isPresent();
        assertThat(result.get().body()).contains("Rendered content");
        assertThat(client.createDriverCalls).isEqualTo(1);
        assertThat(permits.availablePermits()).isEqualTo(1);
        verify(driver).quit();
    }

    @Test
    void returnsEmptyWhenPermitCannotBeAcquired() {
        WebDriver driver = readableDriver(URL);
        Semaphore permits = new Semaphore(0);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, permits, validator);

        Optional<HtmlDocument> result = client.render(URL);

        assertThat(result).isEmpty();
        assertThat(client.createDriverCalls).isZero();
        verify(driver, never()).get(any());
    }

    @Test
    void releasesPermitWhenRenderingFails() {
        Semaphore permits = new Semaphore(1);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(null, permits, validator);
        client.failDriverCreation = true;

        Optional<HtmlDocument> result = client.render(URL);

        assertThat(result).isEmpty();
        assertThat(permits.availablePermits()).isEqualTo(1);
    }

    @Test
    void restoresInterruptedFlagWhenWaitingForPermitIsInterrupted() {
        Semaphore permits = new Semaphore(0);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(readableDriver(URL), permits, validator, 1000);

        Thread.currentThread().interrupt();
        try {
            Optional<HtmlDocument> result = client.render(URL);

            assertThat(result).isEmpty();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(client.createDriverCalls).isZero();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsInitialUnsafeUrlBeforeCreatingDriver() {
        String unsafeUrl = "file:///etc/passwd";
        ExternalHtmlDocumentClient validator = mock(ExternalHtmlDocumentClient.class);
        when(validator.validateFetchTarget(unsafeUrl)).thenThrow(new HtmlFetchException(
                MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED,
                false
        ));
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(readableDriver(unsafeUrl), new Semaphore(1), validator);

        Optional<HtmlDocument> result = client.render(unsafeUrl);

        assertThat(result).isEmpty();
        assertThat(client.createDriverCalls).isZero();
    }

    @Test
    void discardsResultWhenFinalUrlIsUnsafe() {
        String finalUrl = "http://127.0.0.1/admin";
        WebDriver driver = readableDriver(finalUrl);
        ExternalHtmlDocumentClient validator = mock(ExternalHtmlDocumentClient.class);
        when(validator.validateFetchTarget(URL)).thenReturn(URI.create(URL));
        when(validator.validateFetchTarget(finalUrl)).thenThrow(new HtmlFetchException(
                MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED,
                false
        ));
        Semaphore permits = new Semaphore(1);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, permits, validator);

        Optional<HtmlDocument> result = client.render(URL);

        assertThat(result).isEmpty();
        assertThat(permits.availablePermits()).isEqualTo(1);
        verify(driver).quit();
    }

    @Test
    void triggersBoundedScrollForNotionRenderedPages() {
        String notionUrl = "https://example.notion.site/page";
        WebDriver driver = readableDriver(notionUrl);
        Semaphore permits = new Semaphore(1);
        ExternalHtmlDocumentClient validator = validatorAllowing(notionUrl);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, permits, validator);

        Optional<HtmlDocument> result = client.render(notionUrl);

        assertThat(result).isPresent();
        JavascriptExecutor javascriptExecutor = (JavascriptExecutor) driver;
        verify(javascriptExecutor).executeScript(org.mockito.ArgumentMatchers.<String>argThat(script -> script != null
                && script.startsWith("let expandedCount")
                && script.contains("aria-expanded")
                && script.contains("expandedState === 'true'")
                && script.contains("return;")));
        verify(javascriptExecutor).executeScript(startsWith("window.scrollTo"), eq(1));
        verify(javascriptExecutor).executeScript(startsWith("window.scrollTo"), eq(2));
        verify(javascriptExecutor).executeScript(startsWith("window.scrollTo"), eq(3));
        verify(javascriptExecutor).executeScript(startsWith("window.scrollTo"), eq(4));
        verify(javascriptExecutor).executeScript(startsWith("window.scrollTo"), eq(5));
        verify(javascriptExecutor).executeScript(startsWith("window.scrollTo"), eq(6));
        verify(javascriptExecutor).executeScript(startsWith("window.scrollTo"), eq(7));
        verify(javascriptExecutor).executeScript(startsWith("window.scrollTo"), eq(8));
        verify(javascriptExecutor).executeScript("window.scrollTo(0, 0);");
    }

    private ExternalHtmlDocumentClient validatorAllowing(String url) {
        ExternalHtmlDocumentClient validator = mock(ExternalHtmlDocumentClient.class);
        when(validator.validateFetchTarget(url)).thenReturn(URI.create(url));
        return validator;
    }

    private WebDriver readableDriver(String currentUrl) {
        WebDriver driver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
        WebDriver.Options options = mock(WebDriver.Options.class);
        WebDriver.Timeouts timeouts = mock(WebDriver.Timeouts.class);
        when(driver.manage()).thenReturn(options);
        when(options.timeouts()).thenReturn(timeouts);
        when(timeouts.pageLoadTimeout(any(Duration.class))).thenReturn(timeouts);
        when(timeouts.scriptTimeout(any(Duration.class))).thenReturn(timeouts);
        when(driver.getCurrentUrl()).thenReturn(currentUrl);
        when(driver.getPageSource()).thenReturn("<html><body><article>Rendered content with enough text</article></body></html>");
        JavascriptExecutor javascriptExecutor = (JavascriptExecutor) driver;
        when(javascriptExecutor.executeScript("return document.readyState")).thenReturn("complete");
        when(javascriptExecutor.executeScript(
                "return document.body && document.body.innerText ? document.body.innerText.trim().length : 0"
        )).thenReturn(40L);
        return driver;
    }

    private static final class TestRenderedHtmlDocumentClient extends RenderedHtmlDocumentClient {

        private final WebDriver driver;
        private int createDriverCalls;
        private boolean failDriverCreation;

        private TestRenderedHtmlDocumentClient(
                WebDriver driver,
                Semaphore permits,
                ExternalHtmlDocumentClient validator
        ) {
            this(driver, permits, validator, 0);
        }

        private TestRenderedHtmlDocumentClient(
                WebDriver driver,
                Semaphore permits,
                ExternalHtmlDocumentClient validator,
                long acquireTimeoutMs
        ) {
            super(true, 1000, 1000, 100, 20, acquireTimeoutMs, validator, permits);
            this.driver = driver;
        }

        @Override
        protected WebDriver createDriver() {
            createDriverCalls++;
            if (failDriverCreation) {
                throw new IllegalStateException("driver unavailable");
            }
            return driver;
        }
    }
}
