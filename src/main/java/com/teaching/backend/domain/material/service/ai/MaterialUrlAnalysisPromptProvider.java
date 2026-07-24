package com.teaching.backend.domain.material.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MaterialUrlAnalysisPromptProvider {

    private final ResourcePromptLoader resourcePromptLoader;

    @Value("${material.url-analysis.prompt.system:classpath:prompts/material/url-analysis-system-prompt.md}")
    private String systemPromptLocation;

    @Value("${material.url-analysis.prompt.user-template:classpath:prompts/material/url-analysis-user-template.md}")
    private String userTemplateLocation;

    public String systemPrompt() {
        return resourcePromptLoader.load(systemPromptLocation);
    }

    public String userTemplate() {
        return resourcePromptLoader.load(userTemplateLocation);
    }
}
