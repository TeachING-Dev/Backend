package com.teaching.backend.domain.user.entity;

import com.teaching.backend.domain.user.enums.WithdrawalReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "withdrawal_history")
public class WithdrawalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawalReason reason;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @Column(name = "withdrawn_at", nullable = false)
    private LocalDateTime withdrawnAt;

    @Builder(access = AccessLevel.PRIVATE)
    private WithdrawalHistory(Long userId, WithdrawalReason reason, String reasonDetail, LocalDateTime withdrawnAt) {
        this.userId = userId;
        this.reason = reason;
        this.reasonDetail = reasonDetail;
        this.withdrawnAt = withdrawnAt;
    }

    public static WithdrawalHistory of(Long userId, WithdrawalReason reason, String reasonDetail) {
        return WithdrawalHistory.builder()
                .userId(userId)
                .reason(reason)
                .reasonDetail(reasonDetail)
                .withdrawnAt(LocalDateTime.now())
                .build();
    }
}
