package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.function.Supplier;

@Slf4j
@Service
public class MaterialUrlAnalysisConcurrencyGuard {

    private static final String ACQUIRE_LOCK_SQL = "SELECT GET_LOCK(?, ?)";
    private static final String RELEASE_LOCK_SQL = "SELECT RELEASE_LOCK(?)";
    private static final String LOCK_PREFIX = "mat-url:";
    private static final int LOCK_HASH_LENGTH = 56;

    private final DataSource dataSource;
    private final int lockTimeoutSeconds;

    public MaterialUrlAnalysisConcurrencyGuard(
            DataSource dataSource,
            @Value("${material.url-analysis.lock-timeout-seconds:300}") int lockTimeoutSeconds
    ) {
        this.dataSource = dataSource;
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    public <T> T executeWithLock(Long userId, String originalUrl, Supplier<T> action) {
        String lockName = lockName(userId, originalUrl);
        try (Connection connection = dataSource.getConnection()) {
            acquireLock(connection, lockName, userId);
            try {
                return action.get();
            } finally {
                releaseLock(connection, lockName, userId);
            }
        } catch (SQLException e) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED, e);
        }
    }

    private void acquireLock(Connection connection, String lockName, Long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ACQUIRE_LOCK_SQL)) {
            statement.setString(1, lockName);
            statement.setInt(2, lockTimeoutSeconds);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) == 1) {
                    return;
                }
            }
        }

        log.warn("Material URL analysis lock acquisition timed out. userId={}, lockName={}", userId, lockName);
        throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
    }

    private void releaseLock(Connection connection, String lockName, Long userId) {
        try (PreparedStatement statement = connection.prepareStatement(RELEASE_LOCK_SQL)) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    log.warn("Material URL analysis lock was not released normally. userId={}, lockName={}", userId, lockName);
                }
            }
        } catch (SQLException e) {
            log.warn(
                    "Material URL analysis lock release failed. userId={}, lockName={}, reason={}",
                    userId,
                    lockName,
                    e.getClass().getSimpleName()
            );
        }
    }

    private String lockName(Long userId, String originalUrl) {
        String source = "user:%d\nurl:%s".formatted(userId, originalUrl);
        byte[] digest = sha256(source);
        return LOCK_PREFIX + HexFormat.of().formatHex(digest).substring(0, LOCK_HASH_LENGTH);
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
