package com.teaching.backend.domain.material.service.extract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectedSourceDetectorTest {

    @Test
    void rejectsPrivateNotionAppShellForAppDocumentUrl() {
        String originalUrl = "https://app.notion.com/p/private-page-id";
        String html = """
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
                """;

        boolean result = ProtectedSourceDetector.isProtectedHtml(
                html,
                "https://app.notion.com/p/private-page-id",
                originalUrl
        );

        assertThat(result).isTrue();
    }

    @Test
    void doesNotRejectPublicNotionSiteDocument() {
        String html = """
                <html>
                  <head><title>Public Notion Page</title></head>
                  <body>
                    <div class="notion-page-content">
                      <h1>BE 학습 정리</h1>
                      <p>공개 Notion 문서의 실제 본문 내용입니다.</p>
                    </div>
                  </body>
                </html>
                """;

        boolean result = ProtectedSourceDetector.isProtectedHtml(
                html,
                "https://example.notion.site/public-page",
                "https://example.notion.site/public-page"
        );

        assertThat(result).isFalse();
    }

    @Test
    void doesNotRejectGenericWebDocumentMentioningNotionAndLogin() {
        String html = """
                <html>
                  <head><title>Notion login 설명</title></head>
                  <body>
                    <article>
                      <h1>Notion 사용법</h1>
                      <p>Notion login flow와 OAuth 설정을 설명하는 공개 기술 문서입니다.</p>
                    </article>
                  </body>
                </html>
                """;

        boolean result = ProtectedSourceDetector.isProtectedHtml(
                html,
                "https://example.com/articles/notion-login",
                "https://example.com/articles/notion-login"
        );

        assertThat(result).isFalse();
    }

    @Test
    void doesNotRejectByNotionMarketingTitleAlone() {
        String originalUrl = "https://app.notion.com/p/private-page-id";
        String html = """
                <html>
                  <head><title>Notion | Where teams and agents work together</title></head>
                  <body>
                    <article>
                      <h1>Public article</h1>
                      <p>Title alone is not enough to classify this page as protected.</p>
                    </article>
                  </body>
                </html>
                """;

        boolean result = ProtectedSourceDetector.isProtectedHtml(
                html,
                originalUrl,
                originalUrl
        );

        assertThat(result).isFalse();
    }
}
