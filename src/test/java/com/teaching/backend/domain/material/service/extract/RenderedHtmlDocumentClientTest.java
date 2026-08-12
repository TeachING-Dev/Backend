package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.ScriptTimeoutException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void usesEagerStrategyAndLongerTimeoutsForNotion() {
        String notionUrl = "https://example.notion.site/page";
        WebDriver driver = readableDriver(notionUrl);
        WebDriver.Timeouts timeouts = timeouts(driver);
        Semaphore permits = new Semaphore(1);
        ExternalHtmlDocumentClient validator = validatorAllowing(notionUrl);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(
                permits,
                validator,
                10000,
                5000,
                30000,
                10000,
                driver
        );

        Optional<HtmlDocument> result = client.render(notionUrl);

        assertThat(result).isPresent();
        assertThat(client.pageLoadStrategies).containsExactly(PageLoadStrategy.EAGER);
        verify(timeouts).pageLoadTimeout(Duration.ofSeconds(30));
        verify(timeouts).scriptTimeout(Duration.ofSeconds(10));
    }

    @Test
    void keepsNormalStrategyAndDefaultTimeoutsForNonNotion() {
        WebDriver driver = readableDriver(URL);
        WebDriver.Timeouts timeouts = timeouts(driver);
        Semaphore permits = new Semaphore(1);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(
                permits,
                validator,
                10000,
                5000,
                30000,
                10000,
                driver
        );

        Optional<HtmlDocument> result = client.render(URL);

        assertThat(result).isPresent();
        assertThat(client.pageLoadStrategies).containsExactly(PageLoadStrategy.NORMAL);
        verify(timeouts).pageLoadTimeout(Duration.ofSeconds(10));
        verify(timeouts).scriptTimeout(Duration.ofSeconds(5));
    }

    @Test
    void retriesNotionTimeoutWithNewDriver() {
        String notionUrl = "https://example.notion.site/page";
        WebDriver firstDriver = driverFailingOnGet(notionUrl, new TimeoutException("page load timeout"));
        WebDriver secondDriver = readableDriver(notionUrl);
        Semaphore permits = new Semaphore(1);
        ExternalHtmlDocumentClient validator = validatorAllowing(notionUrl);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(
                permits,
                validator,
                firstDriver,
                secondDriver
        );

        Optional<HtmlDocument> result = client.render(notionUrl);

        assertThat(result).isPresent();
        assertThat(client.createDriverCalls).isEqualTo(2);
        assertThat(client.pageLoadStrategies).containsExactly(PageLoadStrategy.EAGER, PageLoadStrategy.EAGER);
        verify(firstDriver).quit();
        verify(secondDriver).quit();
        assertThat(permits.availablePermits()).isEqualTo(1);
    }

    @Test
    void retriesNotionScriptTimeoutWithNewDriver() {
        String notionUrl = "https://example.notion.site/page";
        WebDriver firstDriver = readableDriver(notionUrl);
        JavascriptExecutor javascriptExecutor = (JavascriptExecutor) firstDriver;
        when(javascriptExecutor.executeScript("return document.readyState"))
                .thenThrow(new ScriptTimeoutException("script timeout"));
        WebDriver secondDriver = readableDriver(notionUrl);
        Semaphore permits = new Semaphore(1);
        ExternalHtmlDocumentClient validator = validatorAllowing(notionUrl);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(
                permits,
                validator,
                firstDriver,
                secondDriver
        );

        Optional<HtmlDocument> result = client.render(notionUrl);

        assertThat(result).isPresent();
        assertThat(client.createDriverCalls).isEqualTo(2);
        verify(firstDriver).quit();
        verify(secondDriver).quit();
    }

    @Test
    void stopsAfterTwoNotionTimeoutAttempts() {
        String notionUrl = "https://example.notion.site/page";
        WebDriver firstDriver = driverFailingOnGet(notionUrl, new TimeoutException("first timeout"));
        WebDriver secondDriver = driverFailingOnGet(notionUrl, new TimeoutException("second timeout"));
        Semaphore permits = new Semaphore(1);
        ExternalHtmlDocumentClient validator = validatorAllowing(notionUrl);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(
                permits,
                validator,
                firstDriver,
                secondDriver
        );

        Optional<HtmlDocument> result = client.render(notionUrl);

        assertThat(result).isEmpty();
        assertThat(client.createDriverCalls).isEqualTo(2);
        verify(firstDriver).quit();
        verify(secondDriver).quit();
    }

    @Test
    void doesNotRetryNonTimeoutRuntimeException() {
        WebDriver driver = driverFailingOnGet(URL, new IllegalStateException("browser failed"));
        Semaphore permits = new Semaphore(1);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, permits, validator);

        Optional<HtmlDocument> result = client.render(URL);

        assertThat(result).isEmpty();
        assertThat(client.createDriverCalls).isEqualTo(1);
        verify(driver).quit();
    }

    @Test
    void rejectsAuthenticationFinalUrlWithoutRetry() {
        String finalUrl = "https://example.com/login?redirect=/private/article";
        WebDriver driver = readableDriver(finalUrl);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL, finalUrl);
        Semaphore permits = new Semaphore(1);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, permits, validator);

        assertThatThrownBy(() -> client.render(URL))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_SOURCE_AUTH_REQUIRED);

        assertThat(client.createDriverCalls).isEqualTo(1);
        verify(driver).quit();
    }

    @Test
    void rejectsSigninFinalUrlWithoutRetry() {
        String finalUrl = "https://example.com/signin";
        WebDriver driver = readableDriver(finalUrl);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL, finalUrl);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, new Semaphore(1), validator);

        assertThatThrownBy(() -> client.render(URL))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_SOURCE_AUTH_REQUIRED);

        assertThat(client.createDriverCalls).isEqualTo(1);
        verify(driver).quit();
    }

    @Test
    void rejectsOAuthFinalUrlWithoutRetry() {
        String finalUrl = "https://accounts.google.com/o/oauth2/v2/auth";
        WebDriver driver = readableDriver(finalUrl);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL, finalUrl);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, new Semaphore(1), validator);

        assertThatThrownBy(() -> client.render(URL))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_SOURCE_AUTH_REQUIRED);

        assertThat(client.createDriverCalls).isEqualTo(1);
        verify(driver).quit();
    }

    @Test
    void doesNotRejectLoginWordInQueryOnly() {
        String urlWithLoginQuery = "https://example.com/article?keyword=login";
        WebDriver driver = readableDriver(urlWithLoginQuery);
        ExternalHtmlDocumentClient validator = validatorAllowing(urlWithLoginQuery);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, new Semaphore(1), validator);

        Optional<HtmlDocument> result = client.render(urlWithLoginQuery);

        assertThat(result).isPresent();
        verify(driver).quit();
    }

    @Test
    void doesNotRejectAuthWordInArticlePath() {
        String articleUrl = "https://example.com/articles/auth";
        WebDriver driver = readableDriver(articleUrl);
        ExternalHtmlDocumentClient validator = validatorAllowing(articleUrl);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, new Semaphore(1), validator);

        Optional<HtmlDocument> result = client.render(articleUrl);

        assertThat(result).isPresent();
        verify(driver).quit();
    }

    @Test
    void rejectsRenderedLoginFormPage() {
        WebDriver driver = readableDriver(URL);
        when(driver.getPageSource()).thenReturn("""
                <html>
                  <head><title>Login</title></head>
                  <body><main><h1>Sign in</h1><form action="/login"><input type="password"></form></main></body>
                </html>
                """);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, new Semaphore(1), validator);

        assertThatThrownBy(() -> client.render(URL))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_SOURCE_AUTH_REQUIRED);

        assertThat(client.createDriverCalls).isEqualTo(1);
        verify(driver).quit();
    }

    @Test
    void rejectsPrivateNotionAppShellWithoutRetry() {
        String originalUrl = "https://app.notion.com/p/private-page-id";
        WebDriver driver = readableDriver(originalUrl);
        when(driver.getPageSource()).thenReturn("""
                <html>
                  <head><title>Notion | Where teams and agents work together</title></head>
                  <body>
                    <main>
                      <h1>Where teams and agents work together</h1>
                      <p>Notion is the connected workspace for your docs, projects, and knowledge.</p>
                      <a href="/login">Log in</a>
                      <a href="/signup">Sign up</a>
                    </main>
                  </body>
                </html>
                """);
        ExternalHtmlDocumentClient validator = validatorAllowing(originalUrl);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, new Semaphore(1), validator);

        assertThatThrownBy(() -> client.render(originalUrl))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.MATERIAL_SOURCE_AUTH_REQUIRED);

        assertThat(client.createDriverCalls).isEqualTo(1);
        verify(driver).quit();
    }

    @Test
    void doesNotRetryUnsafeInitialUrl() {
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
    void doesNotRetryTimeoutForNonNotion() {
        WebDriver driver = driverFailingOnGet(URL, new TimeoutException("page load timeout"));
        Semaphore permits = new Semaphore(1);
        ExternalHtmlDocumentClient validator = validatorAllowing(URL);
        TestRenderedHtmlDocumentClient client = new TestRenderedHtmlDocumentClient(driver, permits, validator);

        Optional<HtmlDocument> result = client.render(URL);

        assertThat(result).isEmpty();
        assertThat(client.createDriverCalls).isEqualTo(1);
        assertThat(client.pageLoadStrategies).containsExactly(PageLoadStrategy.NORMAL);
        verify(driver).quit();
    }

    private ExternalHtmlDocumentClient validatorAllowing(String... urls) {
        ExternalHtmlDocumentClient validator = mock(ExternalHtmlDocumentClient.class);
        for (String url : urls) {
            when(validator.validateFetchTarget(url)).thenReturn(URI.create(url));
        }
        return validator;
    }

    private WebDriver driverFailingOnGet(String url, RuntimeException exception) {
        WebDriver driver = readableDriver(url);
        doThrow(exception).when(driver).get(url);
        return driver;
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

    private WebDriver.Timeouts timeouts(WebDriver driver) {
        return driver.manage().timeouts();
    }

    private static final class TestRenderedHtmlDocumentClient extends RenderedHtmlDocumentClient {

        private final List<WebDriver> drivers;
        private final List<PageLoadStrategy> pageLoadStrategies = new ArrayList<>();
        private int createDriverCalls;
        private boolean failDriverCreation;

        private TestRenderedHtmlDocumentClient(
                WebDriver driver,
                Semaphore permits,
                ExternalHtmlDocumentClient validator
        ) {
            this(permits, validator, 0, driver);
        }

        private TestRenderedHtmlDocumentClient(
                WebDriver driver,
                Semaphore permits,
                ExternalHtmlDocumentClient validator,
                long acquireTimeoutMs
        ) {
            this(permits, validator, acquireTimeoutMs, driver);
        }

        private TestRenderedHtmlDocumentClient(
                Semaphore permits,
                ExternalHtmlDocumentClient validator,
                WebDriver... drivers
        ) {
            this(permits, validator, 0, drivers);
        }

        private TestRenderedHtmlDocumentClient(
                Semaphore permits,
                ExternalHtmlDocumentClient validator,
                long acquireTimeoutMs,
                WebDriver... drivers
        ) {
            super(true, 1000, 1000, 30000, 10000, 100, 20, acquireTimeoutMs, validator, permits);
            this.drivers = Arrays.asList(drivers);
        }

        private TestRenderedHtmlDocumentClient(
                Semaphore permits,
                ExternalHtmlDocumentClient validator,
                long pageLoadTimeoutMs,
                long scriptTimeoutMs,
                long notionPageLoadTimeoutMs,
                long notionScriptTimeoutMs,
                WebDriver... drivers
        ) {
            super(true, pageLoadTimeoutMs, scriptTimeoutMs, notionPageLoadTimeoutMs, notionScriptTimeoutMs,
                    100, 20, 0, validator, permits);
            this.drivers = Arrays.asList(drivers);
        }

        @Override
        protected WebDriver createDriver(PageLoadStrategy pageLoadStrategy) {
            createDriverCalls++;
            pageLoadStrategies.add(pageLoadStrategy);
            if (failDriverCreation) {
                throw new IllegalStateException("driver unavailable");
            }
            return drivers.get(createDriverCalls - 1);
        }
    }
}
