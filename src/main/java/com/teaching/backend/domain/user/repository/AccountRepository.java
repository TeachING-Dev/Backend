package com.teaching.backend.domain.user.repository;


import com.teaching.backend.domain.user.entity.Account;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("SELECT a FROM Account a JOIN FETCH a.user WHERE a.provider = :provider AND a.providerAccountId = :providerAccountId")
    Optional<Account> findByProviderAndProviderAccountId(
            @Param("provider") Provider provider,
            @Param("providerAccountId") String providerAccountId
    );

    /** 특정 사용자에 연동된 소셜 계정 목록 (마이페이지 조회 시 provider 노출용) */
    List<Account> findAllByUser(User user);
}