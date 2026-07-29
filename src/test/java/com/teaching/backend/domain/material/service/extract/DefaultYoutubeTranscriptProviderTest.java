package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultYoutubeTranscriptProviderTest {

    @Test
    void selectsManualKoreanTranscriptFirst() {
        DefaultYoutubeTranscriptProvider provider = provider(
                candidate("en", false, "manual English"),
                candidate("ko", true, "generated Korean"),
                candidate("ko", false, "manual Korean")
        );

        Optional<String> result = provider.getTranscript("https://www.youtube.com/watch?v=video");

        assertThat(result).contains("manual Korean");
    }

    @Test
    void selectsGeneratedKoreanBeforeManualEnglish() {
        DefaultYoutubeTranscriptProvider provider = provider(
                candidate("en", false, "manual English"),
                candidate("ko", true, "generated Korean")
        );

        Optional<String> result = provider.getTranscript("https://www.youtube.com/watch?v=video");

        assertThat(result).contains("generated Korean");
    }

    @Test
    void selectsManualEnglishBeforeGeneratedEnglish() {
        DefaultYoutubeTranscriptProvider provider = provider(
                candidate("en", true, "generated English"),
                candidate("en-US", false, "manual English")
        );

        Optional<String> result = provider.getTranscript("https://www.youtube.com/watch?v=video");

        assertThat(result).contains("manual English");
    }

    @Test
    void selectsFirstTranscriptWhenKoreanAndEnglishAreMissing() {
        DefaultYoutubeTranscriptProvider provider = provider(
                candidate("ja", false, "Japanese caption"),
                candidate("fr", false, "French caption")
        );

        Optional<String> result = provider.getTranscript("https://www.youtube.com/watch?v=video");

        assertThat(result).contains("Japanese caption");
    }

    @Test
    void joinsTranscriptFragmentsAsPlainText() {
        DefaultYoutubeTranscriptProvider provider = provider(
                candidate("ko", false, "첫 번째 줄", "두 번째 줄")
        );

        Optional<String> result = provider.getTranscript("https://www.youtube.com/watch?v=video");

        assertThat(result).contains("첫 번째 줄\n두 번째 줄");
    }

    @Test
    void returnsEmptyWhenTranscriptListIsEmpty() {
        DefaultYoutubeTranscriptProvider provider = provider(List.of());

        Optional<String> result = provider.getTranscript("https://www.youtube.com/watch?v=video");

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenTranscriptsAreDisabled() {
        DefaultYoutubeTranscriptProvider provider = new DefaultYoutubeTranscriptProvider(
                videoId -> {
                    throw new TranscriptRetrievalException(videoId, "Transcripts are disabled for this video.");
                }
        );

        Optional<String> result = provider.getTranscript("https://www.youtube.com/watch?v=video");

        assertThat(result).isEmpty();
    }

    @Test
    void throwsExtractionFailureWhenTranscriptListRequestFails() {
        DefaultYoutubeTranscriptProvider provider = new DefaultYoutubeTranscriptProvider(
                videoId -> {
                    throw new TranscriptRetrievalException(videoId, "HTTP request failed");
                }
        );

        assertThatThrownBy(() -> provider.getTranscript("https://www.youtube.com/watch?v=video"))
                .isInstanceOf(MaterialException.class)
                .satisfies(exception -> assertThat(((MaterialException) exception).getErrorCode())
                        .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED));
    }

    @Test
    void throwsExtractionFailureWhenTranscriptFetchFails() {
        DefaultYoutubeTranscriptProvider provider = provider(new DefaultYoutubeTranscriptProvider.TranscriptCandidate(
                "ko",
                false,
                () -> {
                    throw new TranscriptRetrievalException("video", "caption fetch failed");
                }
        ));

        assertThatThrownBy(() -> provider.getTranscript("https://www.youtube.com/watch?v=video"))
                .isInstanceOf(MaterialException.class)
                .satisfies(exception -> assertThat(((MaterialException) exception).getErrorCode())
                        .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED));
    }

    @Test
    void throwsExtractionFailureWhenTranscriptContentIsBlank() {
        DefaultYoutubeTranscriptProvider provider = provider(
                candidate("ko", false, " ", "")
        );

        assertThatThrownBy(() -> provider.getTranscript("https://www.youtube.com/watch?v=video"))
                .isInstanceOf(MaterialException.class)
                .satisfies(exception -> assertThat(((MaterialException) exception).getErrorCode())
                        .isEqualTo(MaterialErrorCode.MATERIAL_CONTENT_EXTRACTION_FAILED));
    }

    @Test
    void extractsVideoIdFromYoutubeWatchUrlWithAdditionalQueryParameters() {
        DefaultYoutubeTranscriptProvider provider = provider(List.of());

        Optional<String> result = provider.extractVideoId(
                "https://www.youtube.com/watch?v=1pZjXnev45A&list=PLcXyemr8ZeoT-_8yBc_p_lVwRRqUaN8ET"
        );

        assertThat(result).contains("1pZjXnev45A");
    }

    @Test
    void extractsVideoIdFromYoutuBeUrl() {
        DefaultYoutubeTranscriptProvider provider = provider(List.of());

        Optional<String> result = provider.extractVideoId("https://youtu.be/M8E6vYAIuzQ");

        assertThat(result).contains("M8E6vYAIuzQ");
    }

    @Test
    void returnsEmptyForMalformedVideoUrl() {
        DefaultYoutubeTranscriptProvider provider = provider(List.of());

        Optional<String> result = provider.getTranscript("not-a-url");

        assertThat(result).isEmpty();
    }

    @Test
    void passesExtractedVideoIdToTranscriptClient() {
        AtomicReference<String> requestedVideoId = new AtomicReference<>();
        DefaultYoutubeTranscriptProvider provider = new DefaultYoutubeTranscriptProvider(videoId -> {
            requestedVideoId.set(videoId);
            return List.of(candidate("ko", false, "caption"));
        });

        Optional<String> result = provider.getTranscript("https://www.youtube.com/watch?v=M8E6vYAIuzQ");

        assertThat(result).contains("caption");
        assertThat(requestedVideoId).hasValue("M8E6vYAIuzQ");
    }

    private DefaultYoutubeTranscriptProvider provider(DefaultYoutubeTranscriptProvider.TranscriptCandidate... candidates) {
        return provider(List.of(candidates));
    }

    private DefaultYoutubeTranscriptProvider provider(List<DefaultYoutubeTranscriptProvider.TranscriptCandidate> candidates) {
        return new DefaultYoutubeTranscriptProvider(videoId -> candidates);
    }

    private DefaultYoutubeTranscriptProvider.TranscriptCandidate candidate(
            String languageCode,
            boolean generated,
            String... texts
    ) {
        return new DefaultYoutubeTranscriptProvider.TranscriptCandidate(
                languageCode,
                generated,
                () -> List.of(texts)
        );
    }
}
