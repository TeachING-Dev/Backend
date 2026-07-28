package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourcePromptLoaderTest {

    private final ResourcePromptLoader loader = new ResourcePromptLoader(new DefaultResourceLoader());

    @Test
    void loadsSystemPromptWithUtf8KoreanText() {
        String prompt = loader.load("classpath:prompts/material/url-analysis-system-prompt.md");

        assertThat(prompt).contains("콘텐츠 분석 에이전트");
        assertThat(prompt).contains("short_summary");
    }

    @Test
    void loadsUserTemplate() {
        String prompt = loader.load("classpath:prompts/material/url-analysis-user-template.md");

        assertThat(prompt).contains("{{ORIGINAL_URL}}");
        assertThat(prompt).contains("{{EXTRACTED_CONTENT}}");
    }

    @Test
    void missingResourceFailsClearly() {
        assertThatThrownBy(() -> loader.load("classpath:prompts/material/missing.md"))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
    }
}
