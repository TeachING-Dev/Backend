package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialUrlAnalysisConcurrencyGuardTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement acquireStatement;

    @Mock
    private PreparedStatement releaseStatement;

    @Mock
    private ResultSet acquireResultSet;

    @Mock
    private ResultSet releaseResultSet;

    @Test
    void executesActionWithMysqlNamedLockAndReleasesSameLock() throws Exception {
        MaterialUrlAnalysisConcurrencyGuard guard = new MaterialUrlAnalysisConcurrencyGuard(dataSource, 300);
        givenLockAcquired();
        givenLockReleased();

        String result = guard.executeWithLock(1L, "https://example.com", () -> "done");

        assertThat(result).isEqualTo("done");
        ArgumentCaptor<String> lockNames = ArgumentCaptor.forClass(String.class);
        verify(acquireStatement).setString(org.mockito.ArgumentMatchers.eq(1), lockNames.capture());
        verify(releaseStatement).setString(org.mockito.ArgumentMatchers.eq(1), lockNames.capture());
        assertThat(lockNames.getAllValues().get(0)).isEqualTo(lockNames.getAllValues().get(1));
        assertThat(lockNames.getAllValues().get(0)).startsWith("mat-url:");
        assertThat(lockNames.getAllValues().get(0)).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void releasesLockWhenActionFails() throws Exception {
        MaterialUrlAnalysisConcurrencyGuard guard = new MaterialUrlAnalysisConcurrencyGuard(dataSource, 300);
        givenLockAcquired();
        givenLockReleased();

        assertThatThrownBy(() -> guard.executeWithLock(1L, "https://example.com", () -> {
            throw new IllegalStateException("boom");
        }))
                .isInstanceOf(IllegalStateException.class);

        verify(releaseStatement).executeQuery();
    }

    @Test
    void timeoutDoesNotExecuteActionOrReleaseUnownedLock() throws Exception {
        MaterialUrlAnalysisConcurrencyGuard guard = new MaterialUrlAnalysisConcurrencyGuard(dataSource, 1);
        AtomicBoolean executed = new AtomicBoolean(false);
        givenConnection();
        when(connection.prepareStatement("SELECT GET_LOCK(?, ?)")).thenReturn(acquireStatement);
        when(acquireStatement.executeQuery()).thenReturn(acquireResultSet);
        when(acquireResultSet.next()).thenReturn(true);
        when(acquireResultSet.getInt(1)).thenReturn(0);

        assertThatThrownBy(() -> guard.executeWithLock(1L, "https://example.com", () -> {
            executed.set(true);
            return "done";
        }))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);

        assertThat(executed).isFalse();
        verify(connection, never()).prepareStatement("SELECT RELEASE_LOCK(?)");
    }

    @Test
    void sqlFailureIsConvertedWithCause() throws Exception {
        MaterialUrlAnalysisConcurrencyGuard guard = new MaterialUrlAnalysisConcurrencyGuard(dataSource, 300);
        SQLException cause = new SQLException("connection");
        when(dataSource.getConnection()).thenThrow(cause);

        assertThatThrownBy(() -> guard.executeWithLock(1L, "https://example.com", () -> "done"))
                .isInstanceOf(MaterialException.class)
                .satisfies(exception -> {
                    MaterialException materialException = (MaterialException) exception;
                    assertThat(materialException.getErrorCode()).isEqualTo(MaterialErrorCode.AI_ANALYSIS_GENERATION_FAILED);
                    assertThat(materialException.getCause()).isSameAs(cause);
                });
    }

    private void givenLockAcquired() throws SQLException {
        givenConnection();
        when(connection.prepareStatement("SELECT GET_LOCK(?, ?)")).thenReturn(acquireStatement);
        when(acquireStatement.executeQuery()).thenReturn(acquireResultSet);
        when(acquireResultSet.next()).thenReturn(true);
        when(acquireResultSet.getInt(1)).thenReturn(1);
    }

    private void givenLockReleased() throws SQLException {
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(releaseStatement);
        when(releaseStatement.executeQuery()).thenReturn(releaseResultSet);
        when(releaseResultSet.next()).thenReturn(true);
        when(releaseResultSet.getInt(1)).thenReturn(1);
    }

    private void givenConnection() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
    }
}
