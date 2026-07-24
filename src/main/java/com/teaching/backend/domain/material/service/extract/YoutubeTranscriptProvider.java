package com.teaching.backend.domain.material.service.extract;

import java.util.Optional;

public interface YoutubeTranscriptProvider {

    Optional<String> getTranscript(String originalUrl);
}
