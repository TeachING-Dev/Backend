package com.teaching.backend.domain.material.service;

import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class MaterialUrlValidator {

    public boolean isValidHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }

        String host = uri.getHost();
        return host != null && !host.isBlank();
    }
}
