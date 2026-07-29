package com.teaching.backend.domain.material.service.extract;

import java.util.Optional;

public class UnsupportedYoutubeTranscriptProvider implements YoutubeTranscriptProvider {

    @Override
    public Optional<String> getTranscript(String originalUrl) {
        return Optional.empty();
    }
}
