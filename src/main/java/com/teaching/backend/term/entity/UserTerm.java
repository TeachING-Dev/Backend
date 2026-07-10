package com.teaching.backend.term.entity;

import com.teaching.backend.global.common.BaseTimeEntity;
import com.teaching.backend.user.entity.User;
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
        name = "user_terms",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_term", columnNames = {"user_id", "term_id"})
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

    public static UserTerm agree(User user, Term term) {
        return UserTerm.builder()
                .user(user)
                .term(term)
                .isAgreed(true)
                .agreedAt(LocalDateTime.now())
                .build();
    }

    //재동의를 위한 인스턴스 메서드
    public void reAgree() {
               this.isAgreed = true;
               this.agreedAt = LocalDateTime.now();
           }
    public void withdraw() {
        this.isAgreed = false;
    }
}
