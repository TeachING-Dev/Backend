package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialUrlAnalysisLockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialUrlAnalysisConcurrencyGuardTest {

    @Mock
    private MaterialUrlAnalysisLockRepository lockRepository;

    @Test
    void executesActionAfterAcquiringOwnershipAndReleasesOwnership() {
        MaterialUrlAnalysisConcurrencyGuard guard = guard(1);
        when(lockRepository.existsByUserIdAndUrlHashAndOwnerToken(eq(1L), anyString(), anyString()))
                .thenReturn(true);

        String result = guard.executeSerialized(
                1L,
                "https://example.com",
                Optional::<String>empty,
                () -> "done"
        );

        assertThat(result).isEqualTo("done");
        ArgumentCaptor<String> urlHashCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ownerTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(lockRepository).acquireOrRefreshStale(
                eq(1L),
                urlHashCaptor.capture(),
                eq("https://example.com"),
                ownerTokenCaptor.capture(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        );
        verify(lockRepository).deleteOwned(1L, urlHashCaptor.getValue(), ownerTokenCaptor.getValue());
        assertThat(urlHashCaptor.getValue()).hasSize(64);
    }

    @Test
    void returnsCompletedResultWithoutAcquiringOwnership() {
        MaterialUrlAnalysisConcurrencyGuard guard = guard(1);

        String result = guard.executeSerialized(
                1L,
                "https://example.com",
                () -> Optional.of("already"),
                () -> "new"
        );

        assertThat(result).isEqualTo("already");
        verify(lockRepository, never()).acquireOrRefreshStale(any(), any(), any(), any(), any(), any());
    }

    @Test
    void waitsForConcurrentOwnerAndReturnsCompletedResultWithoutExecutingAction() {
        MaterialUrlAnalysisConcurrencyGuard guard = guard(1);
        AtomicInteger completedLookups = new AtomicInteger();
        AtomicBoolean actionExecuted = new AtomicBoolean(false);
        when(lockRepository.existsByUserIdAndUrlHashAndOwnerToken(eq(1L), anyString(), anyString()))
                .thenReturn(false);

        String result = guard.executeSerialized(
                1L,
                "https://example.com",
                () -> completedLookups.incrementAndGet() < 2 ? Optional.empty() : Optional.of("completed"),
                () -> {
                    actionExecuted.set(true);
                    return "new";
                }
        );

        assertThat(result).isEqualTo("completed");
        assertThat(actionExecuted).isFalse();
        verify(lockRepository).acquireOrRefreshStale(any(), any(), any(), any(), any(), any());
        verify(lockRepository, never()).deleteOwned(any(), any(), any());
    }

    @Test
    void releasesOwnershipWhenActionFailsAndAllowsRetry() {
        MaterialUrlAnalysisConcurrencyGuard guard = guard(1);
        when(lockRepository.existsByUserIdAndUrlHashAndOwnerToken(eq(1L), anyString(), anyString()))
                .thenReturn(true);

        assertThatThrownBy(() -> guard.executeSerialized(
                1L,
                "https://example.com",
                Optional::<String>empty,
                () -> {
                    throw new IllegalStateException("boom");
                }
        ))
                .isInstanceOf(IllegalStateException.class);

        verify(lockRepository).deleteOwned(eq(1L), anyString(), anyString());
    }

    @Test
    void retriesAfterFailedAttemptReleasesOwnership() {
        MaterialUrlAnalysisConcurrencyGuard guard = guard(1);
        when(lockRepository.existsByUserIdAndUrlHashAndOwnerToken(eq(1L), anyString(), anyString()))
                .thenReturn(true);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeSerialized(
                1L,
                "https://example.com",
                Optional::<String>empty,
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("boom");
                }
        ))
                .isInstanceOf(IllegalStateException.class);

        String retry = guard.executeSerialized(
                1L,
                "https://example.com",
                Optional::<String>empty,
                () -> {
                    attempts.incrementAndGet();
                    return "done";
                }
        );

        assertThat(retry).isEqualTo("done");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void timesOutWithoutExecutingActionWhenOwnershipIsNotAvailable() {
        MaterialUrlAnalysisConcurrencyGuard guard = guard(1);
        AtomicBoolean actionExecuted = new AtomicBoolean(false);
        when(lockRepository.existsByUserIdAndUrlHashAndOwnerToken(eq(1L), anyString(), anyString()))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.executeSerialized(
                1L,
                "https://example.com",
                Optional::<String>empty,
                () -> {
                    actionExecuted.set(true);
                    return "new";
                }
        ))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);

        assertThat(actionExecuted).isFalse();
    }

    @Test
    void differentUserAndDifferentUrlUseDifferentCoordinationKeys() {
        MaterialUrlAnalysisConcurrencyGuard guard = guard(1);
        when(lockRepository.existsByUserIdAndUrlHashAndOwnerToken(any(), anyString(), anyString()))
                .thenReturn(true);

        guard.executeSerialized(1L, "https://example.com/a", Optional::<String>empty, () -> "a");
        guard.executeSerialized(2L, "https://example.com/a", Optional::<String>empty, () -> "b");
        guard.executeSerialized(1L, "https://example.com/b", Optional::<String>empty, () -> "c");

        ArgumentCaptor<String> urlHashes = ArgumentCaptor.forClass(String.class);
        verify(lockRepository, org.mockito.Mockito.times(3)).acquireOrRefreshStale(
                any(),
                urlHashes.capture(),
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        );
        assertThat(urlHashes.getAllValues()).doesNotHaveDuplicates();
    }

    private MaterialUrlAnalysisConcurrencyGuard guard(long waitTimeoutSeconds) {
        return new MaterialUrlAnalysisConcurrencyGuard(
                lockRepository,
                new NoOpTransactionManager(),
                java.time.Clock.systemDefaultZone(),
                waitTimeoutSeconds,
                10,
                60
        );
    }

    private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
