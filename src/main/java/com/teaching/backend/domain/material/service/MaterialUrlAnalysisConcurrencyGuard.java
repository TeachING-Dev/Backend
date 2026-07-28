package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialUrlAnalysisLockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
public class MaterialUrlAnalysisConcurrencyGuard {

    private static final int URL_HASH_LENGTH = 64;

    private final MaterialUrlAnalysisLockRepository lockRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final long waitTimeoutMillis;
    private final long pollIntervalMillis;
    private final long ownershipTtlSeconds;

    @Autowired
    public MaterialUrlAnalysisConcurrencyGuard(
            MaterialUrlAnalysisLockRepository lockRepository,
            PlatformTransactionManager transactionManager,
            @Value("${material.url-analysis.lock-timeout-seconds:300}") long waitTimeoutSeconds,
            @Value("${material.url-analysis.lock-poll-interval-ms:200}") long pollIntervalMillis,
            @Value("${material.url-analysis.lock-ttl-seconds:3600}") long ownershipTtlSeconds
    ) {
        this(lockRepository, transactionManager, Clock.systemDefaultZone(), waitTimeoutSeconds, pollIntervalMillis, ownershipTtlSeconds);
    }

    MaterialUrlAnalysisConcurrencyGuard(
            MaterialUrlAnalysisLockRepository lockRepository,
            PlatformTransactionManager transactionManager,
            Clock clock,
            long waitTimeoutSeconds,
            long pollIntervalMillis,
            long ownershipTtlSeconds
    ) {
        this.lockRepository = lockRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.waitTimeoutMillis = Math.max(1, waitTimeoutSeconds) * 1_000;
        this.pollIntervalMillis = Math.max(10, pollIntervalMillis);
        this.ownershipTtlSeconds = Math.max(60, ownershipTtlSeconds);
    }

    public <T> T executeSerialized(
            Long userId,
            String originalUrl,
            Supplier<Optional<T>> completedResult,
            Supplier<T> action
    ) {
        String urlHash = urlHash(userId, originalUrl);
        long deadline = clock.millis() + waitTimeoutMillis;
        while (true) {
            Optional<T> completed = completedResult.get();
            if (completed.isPresent()) {
                return completed.get();
            }

            String ownerToken = UUID.randomUUID().toString();
            if (tryAcquire(userId, urlHash, originalUrl, ownerToken)) {
                try {
                    completed = completedResult.get();
                    if (completed.isPresent()) {
                        return completed.get();
                    }
                    return action.get();
                } finally {
                    release(userId, urlHash, ownerToken);
                }
            }

            if (clock.millis() >= deadline) {
                log.warn("Material URL analysis ownership wait timed out. userId={}, urlHash={}", userId, urlHash);
                throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
            }
            sleepBeforeRetry();
        }
    }

    private boolean tryAcquire(Long userId, String urlHash, String originalUrl, String ownerToken) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime expiresAt = now.plusSeconds(ownershipTtlSeconds);
            lockRepository.acquireOrRefreshStale(
                    userId,
                    urlHash,
                    originalUrl,
                    ownerToken,
                    expiresAt,
                    now
            );
            return lockRepository.existsByUserIdAndUrlHashAndOwnerToken(userId, urlHash, ownerToken);
        }));
    }

    private void release(Long userId, String urlHash, String ownerToken) {
        try {
            transactionTemplate.executeWithoutResult(status -> lockRepository.deleteOwned(userId, urlHash, ownerToken));
        } catch (RuntimeException e) {
            log.warn(
                    "Material URL analysis ownership release failed. userId={}, urlHash={}, reason={}",
                    userId,
                    urlHash,
                    e.getClass().getSimpleName()
            );
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(pollIntervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED, e);
        }
    }

    private String urlHash(Long userId, String originalUrl) {
        String source = "user:%d\nurl:%s".formatted(userId, originalUrl);
        return HexFormat.of().formatHex(sha256(source)).substring(0, URL_HASH_LENGTH);
    }

    private byte[] sha256(String source) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED, e);
        }
    }
}
