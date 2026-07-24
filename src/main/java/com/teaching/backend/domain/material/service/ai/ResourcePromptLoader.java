package com.teaching.backend.domain.material.service.ai;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ResourcePromptLoader {

    private final ResourceLoader resourceLoader;

    public String load(String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (MaterialException e) {
            throw e;
        } catch (Exception e) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
        }
    }
}
