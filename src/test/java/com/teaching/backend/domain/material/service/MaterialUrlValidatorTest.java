package com.teaching.backend.domain.material.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialUrlValidatorTest {

    private final MaterialUrlValidator validator = new MaterialUrlValidator();

    @Test
    void acceptsHttpsUrl() {
        assertThat(validator.isValidHttpUrl("https://example.com/article")).isTrue();
    }

    @Test
    void acceptsHttpUrl() {
        assertThat(validator.isValidHttpUrl("http://example.com/article")).isTrue();
    }

    @Test
    void rejectsNull() {
        assertThat(validator.isValidHttpUrl(null)).isFalse();
    }

    @Test
    void rejectsBlank() {
        assertThat(validator.isValidHttpUrl("   ")).isFalse();
    }

    @Test
    void rejectsUrlWithoutScheme() {
        assertThat(validator.isValidHttpUrl("example.com/article")).isFalse();
    }

    @Test
    void rejectsFtpUrl() {
        assertThat(validator.isValidHttpUrl("ftp://example.com/file")).isFalse();
    }

    @Test
    void rejectsUrlWithoutHost() {
        assertThat(validator.isValidHttpUrl("https:///article")).isFalse();
    }

    @Test
    void rejectsUnparseableUrl() {
        assertThat(validator.isValidHttpUrl("https://exa mple.com/article")).isFalse();
    }
}
