package com.teaching.backend.user.repository;

import com.teaching.backend.user.entity.Account;
import com.teaching.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** 특정 사용자에 연동된 소셜 계정 목록 (마이페이지 조회 시 provider 노출용) */
    List<Account> findAllByUser(User user);
}
