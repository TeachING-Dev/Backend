package com.teaching.backend.domain.user.repository;


import com.teaching.backend.domain.user.entity.Account;
import com.teaching.backend.domain.user.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("SELECT a FROM Account a JOIN FETCH a.user WHERE a.provider = :provider AND a.providerAccountId = :providerAccountId")
    Optional<Account> findByProviderAndProviderAccountId(
            @Param("provider") Provider provider,
            @Param("providerAccountId") String providerAccountId
    );
}