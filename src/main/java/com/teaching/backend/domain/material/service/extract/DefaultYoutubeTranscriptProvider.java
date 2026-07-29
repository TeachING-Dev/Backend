package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import io.github.thoroldvix.api.Transcript;
import io.github.thoroldvix.api.TranscriptApiFactory;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptList;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
public class DefaultYoutubeTranscriptProvider implements YoutubeTranscriptProvider {

    private static final List<String> KOREAN_LANGUAGES = List.of("ko");
    private static final List<String> ENGLISH_LANGUAGES = List.of("en");

    private final TranscriptClient transcriptClient;

    public DefaultYoutubeTranscriptProvider() {
        this(new LibraryTranscriptClient(TranscriptApiFactory.createDefault()));
    }

    DefaultYoutubeTranscriptProvider(TranscriptClient transcriptClient) {
        this.transcriptClient = transcriptClient;
    }

    @Override
    public Optional<String> getTranscript(String originalUrl) {
        Optional<String> videoId = extractVideoId(originalUrl);
        if (videoId.isEmpty()) {
            return Optional.empty();
        }

        List<TranscriptCandidate> candidates = listTranscriptCandidates(videoId.get());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        TranscriptCandidate selected = selectTranscript(candidates).orElseThrow(
                () -> new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED)
        );

        try {
            Optional<String> transcript = normalizeTranscript(String.join("\n", selected.fetchTexts()));
            if (transcript.isEmpty()) {
                log.warn(
                        "YouTube transcript content was empty. videoId={}, languageCode={}, generated={}",
                        videoId.get(),
                        selected.languageCode(),
                        selected.generated()
                );
                throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED);
            }
            return transcript;
        } catch (MaterialException e) {
            throw e;
        } catch (TranscriptRetrievalException e) {
            log.warn(
                    "YouTube transcript content fetch failed. videoId={}, languageCode={}, generated={}, reason={}",
                    videoId.get(),
                    selected.languageCode(),
                    selected.generated(),
                    e.getMessage()
            );
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED, e);
        } catch (RuntimeException e) {
            log.warn(
                    "YouTube transcript content processing failed. videoId={}, languageCode={}, generated={}, reason={}, message={}",
                    videoId.get(),
                    selected.languageCode(),
                    selected.generated(),
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED, e);
        }
    }

    private List<TranscriptCandidate> listTranscriptCandidates(String videoId) {
        try {
            List<TranscriptCandidate> candidates = transcriptClient.listTranscripts(videoId);
            log.debug("YouTube transcript candidates listed. videoId={}, candidateCount={}", videoId, candidates.size());
            return candidates;
        } catch (TranscriptRetrievalException e) {
            if (isTranscriptUnavailable(e)) {
                log.debug("YouTube transcript unavailable. videoId={}, reason={}", videoId, e.getMessage());
                return List.of();
            }
            log.warn("YouTube transcript list failed. videoId={}, reason={}", videoId, e.getMessage());
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED, e);
        } catch (RuntimeException e) {
            log.warn(
                    "YouTube transcript list processing failed. videoId={}, reason={}, message={}",
                    videoId,
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
            throw new MaterialException(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED, e);
        }
    }

    private boolean isTranscriptUnavailable(TranscriptRetrievalException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("transcripts are disabled")
                || normalized.contains("failed to find captions track list")
                || normalized.contains("no transcripts were found");
    }

    private Optional<TranscriptCandidate> selectTranscript(List<TranscriptCandidate> candidates) {
        return candidates.stream()
                .min(Comparator
                        .comparingInt((TranscriptCandidate candidate) -> languagePriority(candidate.languageCode()))
                        .thenComparingInt(candidate -> candidate.generated() ? 1 : 0));
    }

    private int languagePriority(String languageCode) {
        if (matchesAnyLanguage(languageCode, KOREAN_LANGUAGES)) {
            return 0;
        }
        if (matchesAnyLanguage(languageCode, ENGLISH_LANGUAGES)) {
            return 2;
        }
        return 4;
    }

    private boolean matchesAnyLanguage(String languageCode, List<String> targetLanguages) {
        String normalized = languageCode == null ? "" : languageCode.toLowerCase(Locale.ROOT);
        return targetLanguages.stream()
                .anyMatch(target -> normalized.equals(target) || normalized.startsWith(target + "-"));
    }

    Optional<String> extractVideoId(String originalUrl) {
        try {
            URI uri = URI.create(originalUrl);
            String host = uri.getHost();
            if (host == null) {
                return Optional.empty();
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (normalizedHost.equals("youtu.be")) {
                String path = uri.getPath();
                if (path == null || path.length() <= 1) {
                    return Optional.empty();
                }
                String videoId = path.substring(1).split("/", 2)[0];
                return nonBlank(urlDecode(videoId));
            }

            if (normalizedHost.equals("youtube.com") || normalizedHost.endsWith(".youtube.com")) {
                return queryParameter(uri.getRawQuery(), "v").flatMap(this::nonBlank);
            }
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private Optional<String> queryParameter(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Optional.empty();
        }

        for (String pair : rawQuery.split("&")) {
            int delimiter = pair.indexOf('=');
            String key = delimiter < 0 ? pair : pair.substring(0, delimiter);
            if (name.equals(urlDecode(key))) {
                String value = delimiter < 0 ? "" : pair.substring(delimiter + 1);
                return Optional.of(urlDecode(value));
            }
        }
        return Optional.empty();
    }

    private Optional<String> normalizeTranscript(String text) {
        if (text == null) {
            return Optional.empty();
        }

        String normalized = text.replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return normalized.isBlank() ? Optional.empty() : Optional.of(normalized);
    }

    private Optional<String> nonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    interface TranscriptClient {

        List<TranscriptCandidate> listTranscripts(String videoId) throws TranscriptRetrievalException;
    }

    record TranscriptCandidate(
            String languageCode,
            boolean generated,
            TranscriptContentFetcher contentFetcher
    ) {

        List<String> fetchTexts() throws TranscriptRetrievalException {
            return contentFetcher.fetchTexts();
        }
    }

    interface TranscriptContentFetcher {

        List<String> fetchTexts() throws TranscriptRetrievalException;
    }

    private static class LibraryTranscriptClient implements TranscriptClient {

        private final YoutubeTranscriptApi youtubeTranscriptApi;

        private LibraryTranscriptClient(YoutubeTranscriptApi youtubeTranscriptApi) {
            this.youtubeTranscriptApi = youtubeTranscriptApi;
        }

        @Override
        public List<TranscriptCandidate> listTranscripts(String videoId) throws TranscriptRetrievalException {
            TranscriptList transcriptList = youtubeTranscriptApi.listTranscripts(videoId);
            List<TranscriptCandidate> candidates = new ArrayList<>();
            for (Transcript transcript : transcriptList) {
                candidates.add(new TranscriptCandidate(
                        transcript.getLanguageCode(),
                        transcript.isGenerated(),
                        () -> fetchTexts(transcript)
                ));
            }
            return candidates;
        }

        private List<String> fetchTexts(Transcript transcript) throws TranscriptRetrievalException {
            TranscriptContent content = transcript.fetch();
            return content.getContent()
                    .stream()
                    .map(TranscriptContent.Fragment::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
        }
    }
}
