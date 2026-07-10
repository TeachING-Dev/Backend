package com.teaching.backend.user.entity;

import com.teaching.backend.global.common.BaseTimeEntity;
import com.teaching.backend.user.enums.Provider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "accounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_account_id"})
)
public class Account extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(name = "provider_account_id", nullable = false)
    private String providerAccountId;

    public static Account create(User user, Provider provider, String providerAccountId) {
        return Account.builder()
                .user(user)
                .provider(provider)
                .providerAccountId(providerAccountId)
                .build();
    }
}
