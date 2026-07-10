package com.teaching.backend.domain;

import com.teaching.backend.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name="user_terms",
        uniqueConstraints = @UniqueConstraint(name="uk_user_term",columnNames = {"user_id","term_id"})
)
public class UserTerm extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(nullable = false)
    private Boolean isAgreed;

    @Column(nullable = false)
    private LocalDateTime agreedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private UserTerm(User user, Term term, Boolean isAgreed, LocalDateTime agreedAt) {
        this.user = user;
        this.term = term;
        this.isAgreed = isAgreed;
        this.agreedAt = agreedAt;
    }

    // 동의 처리 - 생성 시점에 항상 동의 완료 + 현재 시각 기록
    public static UserTerm agree(User user, Term term) {
        return UserTerm.builder()
                .user(user)
                .term(term)
                .isAgreed(true)
                .agreedAt(LocalDateTime.now())
                .build();
    }

    // 동의 철회 (필요한 경우 - 선택 약관 등)
    public void withdraw() {
        this.isAgreed = false;
    }

}
