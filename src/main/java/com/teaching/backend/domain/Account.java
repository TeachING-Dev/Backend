package com.teaching.backend.domain;

import com.teaching.backend.domain.common.BaseSoftDeleteEntity;
import com.teaching.backend.domain.common.BaseTimeEntity;
import com.teaching.backend.domain.enums.Provider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;


@Entity
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor(access= AccessLevel.PROTECTED)
@Builder(access= AccessLevel.PRIVATE)
//두 컬럼 조합의 중복 방지 !
@Table(
        name = "accounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_account_id"})
)
public class Account extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id",nullable=false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private Provider provider;

    //제공자 계정아이디
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
