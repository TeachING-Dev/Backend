package com.teaching.backend.domain.material.service.extract;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UnsupportedYoutubeTranscriptProvider implements YoutubeTranscriptProvider {

    @Override
    public Optional<String> getTranscript(String originalUrl) {
        return Optional.empty();
    }
}
